package com.devops.dvmp;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Roles:
 *  USER     - upload datasets and new versions (own datasets), submit for review
 *  REVIEWER - approve / reject datasets in review (not their own), download files
 *  ADMIN    - everything, including edit/delete of any dataset
 */
@Controller
public class DatasetController {

    static final List<String> LICENSES = List.of(
            "CC0: Public Domain", "CC BY 4.0", "CC BY-SA 4.0", "ODbL 1.0", "MIT", "Other", "Unknown");

    private final DatasetRepository repo;
    private final DatasetVersionRepository versionRepo;
    private final FileStorageService storage;
    private final String env;

    public DatasetController(DatasetRepository repo, DatasetVersionRepository versionRepo,
                             FileStorageService storage, @Value("${app.env:local}") String env) {
        this.repo = repo; this.versionRepo = versionRepo; this.storage = storage; this.env = env;
    }

    // ---------- common model attributes ----------
    @ModelAttribute("username") String username(Authentication a) { return a == null ? null : a.getName(); }
    @ModelAttribute("role") String role(Authentication a) { return roleOf(a); }
    @ModelAttribute("env") String env() { return env; }
    @ModelAttribute("licenses") List<String> licenses() { return LICENSES; }

    static String roleOf(Authentication a) {
        if (a == null) return null;
        for (GrantedAuthority g : a.getAuthorities())
            if (g.getAuthority().startsWith("ROLE_")) return g.getAuthority().substring(5);
        return null;
    }
    private static boolean isReviewer(String role) { return "REVIEWER".equals(role) || "ADMIN".equals(role); }
    private static boolean canEdit(Dataset d, Authentication a) {
        String role = roleOf(a);
        return "ADMIN".equals(role) || ("USER".equals(role) && d.getOwner().equals(a.getName()));
    }
    private Dataset find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dataset not found"));
    }
    private String back(Long id, RedirectAttributes ra, String key, String msg) {
        ra.addFlashAttribute(key, msg);
        return "redirect:/datasets/" + id;
    }
    static String cleanTags(String raw) {
        if (raw == null) return null;
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : raw.split(",")) {
            String c = t.trim().toLowerCase().replaceAll("[^a-z0-9 _-]", "").replace(' ', '-');
            if (!c.isEmpty()) set.add(c);
            if (set.size() == 8) break;
        }
        return set.isEmpty() ? null : String.join(",", set);
    }
    private static String license(String l) { return LICENSES.contains(l) ? l : "Unknown"; }

    // ---------- pages ----------
    @RequestMapping("/login") String login() { return "login"; }
    @RequestMapping("/denied") String denied() { return "denied"; }
    @GetMapping("/guide") String guide() { return "guide"; }

    @GetMapping("/")
    String index(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                 @RequestParam(required = false) String tag, Model m) {
        List<Dataset> all = repo.findAll(Sort.by(Sort.Direction.DESC, "id"));
        List<Dataset> list = (q == null || q.isBlank()) ? all : repo.search(q.trim());
        if (status != null && !status.isBlank())
            list = list.stream().filter(d -> d.getStatus().name().equals(status)).toList();
        if (tag != null && !tag.isBlank())
            list = list.stream().filter(d -> d.getTagList().contains(tag.toLowerCase())).toList();

        Map<String, Long> counts = all.stream().flatMap(d -> d.getTagList().stream())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        List<String> topTags = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(10).map(Map.Entry::getKey).toList();

        m.addAttribute("datasets", list);
        m.addAttribute("q", q);
        m.addAttribute("statusFilter", status);
        m.addAttribute("tagFilter", tag);
        m.addAttribute("topTags", topTags);
        m.addAttribute("total", repo.count());
        m.addAttribute("draft", repo.countByStatus(Status.DRAFT));
        m.addAttribute("review", repo.countByStatus(Status.IN_REVIEW));
        m.addAttribute("approved", repo.countByStatus(Status.APPROVED));
        return "index";
    }

    // ---------- upload a new dataset (USER / ADMIN) ----------
    @GetMapping("/datasets/new")
    String newForm(Model m) {
        m.addAttribute("dataset", new DatasetForm());
        m.addAttribute("action", "/datasets");
        m.addAttribute("showFile", true);
        return "form";
    }

    @PostMapping("/datasets")
    String create(@Valid @ModelAttribute("dataset") DatasetForm form, BindingResult br,
                  @RequestParam(value = "file", required = false) MultipartFile file,
                  Authentication auth, Model m) {
        String fileError = storage.validate(file);
        if (br.hasErrors() || fileError != null) {
            m.addAttribute("action", "/datasets");
            m.addAttribute("showFile", true);
            m.addAttribute("fileError", fileError);
            return "form";
        }
        StoredFile sf = storage.store(file);
        Dataset d = new Dataset(form.getName().trim(), form.getDescription(), sf.format(), auth.getName());
        d.setSubtitle(form.getSubtitle());
        d.setTags(cleanTags(form.getTags()));
        d.setLicense(license(form.getLicense()));
        d.addVersion(new DatasetVersion(1, "Initial upload", sf, auth.getName()));
        repo.save(d);
        return "redirect:/datasets/" + d.getId();
    }

    // ---------- dataset page ----------
    @GetMapping("/datasets/{id}")
    String view(@PathVariable Long id, @RequestParam(required = false) Integer v, Authentication auth, Model m) {
        Dataset d = find(id);
        DatasetVersion selected = d.getLatest();
        if (v != null) for (DatasetVersion x : d.getVersions()) if (x.getVersionNo() == v) selected = x;
        boolean edit = canEdit(d, auth);
        m.addAttribute("d", d);
        m.addAttribute("selected", selected);
        m.addAttribute("preview", selected == null ? null : storage.preview(selected.getStoredName(), selected.getFileName()));
        m.addAttribute("canEdit", edit);
        m.addAttribute("canSubmit", edit && d.getStatus() == Status.DRAFT);
        m.addAttribute("canAddVersion", edit && d.getStatus() != Status.IN_REVIEW);
        m.addAttribute("canReview", d.getStatus() == Status.IN_REVIEW && isReviewer(roleOf(auth))
                && !d.getOwner().equals(auth.getName()));
        m.addAttribute("isAdmin", "ADMIN".equals(roleOf(auth)));
        return "detail";
    }

    // ---------- edit details ----------
    @GetMapping("/datasets/{id}/edit")
    String editForm(@PathVariable Long id, Authentication auth, Model m, RedirectAttributes ra) {
        Dataset d = find(id);
        if (!canEdit(d, auth)) return back(id, ra, "error", "You are not allowed to edit this dataset");
        m.addAttribute("dataset", new DatasetForm(d));
        m.addAttribute("action", "/datasets/" + id);
        m.addAttribute("showFile", false);
        return "form";
    }

    @PostMapping("/datasets/{id}")
    String update(@PathVariable Long id, @Valid @ModelAttribute("dataset") DatasetForm form, BindingResult br,
                  Authentication auth, Model m, RedirectAttributes ra) {
        Dataset d = find(id);
        if (!canEdit(d, auth)) return back(id, ra, "error", "You are not allowed to edit this dataset");
        if (br.hasErrors()) {
            m.addAttribute("action", "/datasets/" + id);
            m.addAttribute("showFile", false);
            return "form";
        }
        d.setName(form.getName().trim());
        d.setSubtitle(form.getSubtitle());
        d.setDescription(form.getDescription());
        d.setTags(cleanTags(form.getTags()));
        d.setLicense(license(form.getLicense()));
        repo.save(d);
        return back(id, ra, "success", "Dataset details updated");
    }

    // ---------- upload a new version ----------
    @PostMapping("/datasets/{id}/versions")
    String addVersion(@PathVariable Long id, @RequestParam(defaultValue = "") String notes,
                      @RequestParam(value = "file", required = false) MultipartFile file,
                      Authentication auth, RedirectAttributes ra) {
        Dataset d = find(id);
        if (!canEdit(d, auth)) return back(id, ra, "error", "You can only add versions to your own datasets");
        if (d.getStatus() == Status.IN_REVIEW) return back(id, ra, "error", "Dataset is under review - wait for the decision");
        String err = storage.validate(file);
        if (err != null) return back(id, ra, "error", err);

        StoredFile sf = storage.store(file);
        int next = d.getCurrentVersion() + 1;
        d.addVersion(new DatasetVersion(next, notes.isBlank() ? "No notes" : notes.trim(), sf, auth.getName()));
        if (d.getStatus() == Status.APPROVED) d.setStatus(Status.DRAFT);   // a new version needs re-approval
        repo.save(d);
        return back(id, ra, "success", "Version v" + next + " uploaded");
    }

    // ---------- workflow ----------
    @PostMapping("/datasets/{id}/submit")
    String submit(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        Dataset d = find(id);
        if (!canEdit(d, auth) || d.getStatus() != Status.DRAFT)
            return back(id, ra, "error", "You cannot submit this dataset");
        d.setStatus(Status.IN_REVIEW);
        d.setReviewNote(null);
        repo.save(d);
        return back(id, ra, "success", "Submitted for review");
    }

    @PostMapping("/datasets/{id}/approve")
    String approve(@PathVariable Long id, @RequestParam(defaultValue = "") String comment,
                   Authentication auth, RedirectAttributes ra) {
        return review(id, true, comment, auth, ra);
    }

    @PostMapping("/datasets/{id}/reject")
    String reject(@PathVariable Long id, @RequestParam(defaultValue = "") String comment,
                  Authentication auth, RedirectAttributes ra) {
        return review(id, false, comment, auth, ra);
    }

    private String review(Long id, boolean approve, String comment, Authentication auth, RedirectAttributes ra) {
        Dataset d = find(id);
        if (d.getStatus() != Status.IN_REVIEW) return back(id, ra, "error", "Dataset is not in review");
        if (d.getOwner().equals(auth.getName()))
            return back(id, ra, "error", "You cannot review your own dataset");
        d.setStatus(approve ? Status.APPROVED : Status.DRAFT);
        d.setReviewedBy(auth.getName());
        d.setReviewNote(comment.isBlank() ? null : comment.trim());
        repo.save(d);
        return back(id, ra, "success", approve ? "Dataset approved" : "Dataset rejected and returned to draft");
    }

    // ---------- delete (ADMIN) ----------
    @PostMapping("/datasets/{id}/delete")
    String delete(@PathVariable Long id) {
        Dataset d = find(id);
        d.getVersions().forEach(v -> storage.delete(v.getStoredName()));
        repo.delete(d);
        return "redirect:/";
    }

    // ---------- download (any signed-in user) ----------
    @GetMapping("/versions/{id}/download")
    ResponseEntity<Resource> download(@PathVariable Long id) {
        DatasetVersion v = versionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (v.getStoredName() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        v.addDownload();
        versionRepo.save(v);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(v.getFileName()).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(v.getFileSize())
                .body(storage.load(v.getStoredName()));
    }
}
