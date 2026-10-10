package com.devops.dvmp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DatasetControllerTest {

    @Autowired MockMvc mvc;
    @Autowired DatasetRepository repo;

    private Dataset inReview(String owner) {
        Dataset d = new Dataset("Workflow DS", "x", "CSV", owner);
        d.setStatus(Status.IN_REVIEW);
        return repo.save(d);
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void guidePageIsPublic() throws Exception {
        mvc.perform(get("/guide")).andExpect(status().isOk()).andExpect(content().string(containsString("Permission matrix")));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test @WithMockUser(username = "user", roles = "USER")
    void userSeesDashboard() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Datasets")));
    }

    @Test @WithMockUser(username = "user", roles = "USER")
    void userCanUploadDataset() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "data.csv", "text/csv", "a,b\n1,2\n".getBytes());
        mvc.perform(multipart("/datasets").file(f).param("name", "Unit DS").with(csrf()))
           .andExpect(status().is3xxRedirection());
    }

    @Test @WithMockUser(username = "user", roles = "USER")
    void uploadWithoutFileIsRejected() throws Exception {
        mvc.perform(multipart("/datasets").param("name", "No file").with(csrf()))
           .andExpect(status().isOk()).andExpect(view().name("form")).andExpect(model().attributeExists("fileError"));
    }

    @Test @WithMockUser(username = "user", roles = "USER")
    void badFileTypeIsRejected() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "x".getBytes());
        mvc.perform(multipart("/datasets").file(f).param("name", "Bad").with(csrf()))
           .andExpect(status().isOk()).andExpect(model().attributeExists("fileError"));
    }

    @Test @WithMockUser(username = "reviewer", roles = "REVIEWER")
    void reviewerCannotUpload() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "data.csv", "text/csv", "a".getBytes());
        mvc.perform(multipart("/datasets").file(f).param("name", "X").with(csrf()))
           .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username = "user", roles = "USER")
    void userCannotApprove() throws Exception {
        Dataset d = inReview("user2");
        mvc.perform(post("/datasets/" + d.getId() + "/approve").with(csrf())).andExpect(status().isForbidden());
        assertEquals(Status.IN_REVIEW, repo.findById(d.getId()).get().getStatus());
    }

    @Test @WithMockUser(username = "reviewer", roles = "REVIEWER")
    void reviewerCanApprove() throws Exception {
        Dataset d = inReview("user");
        mvc.perform(post("/datasets/" + d.getId() + "/approve").with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals(Status.APPROVED, repo.findById(d.getId()).get().getStatus());
    }

    @Test @WithMockUser(username = "admin", roles = "ADMIN")
    void nobodyCanApproveOwnDataset() throws Exception {
        Dataset d = inReview("admin");
        mvc.perform(post("/datasets/" + d.getId() + "/approve").with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals(Status.IN_REVIEW, repo.findById(d.getId()).get().getStatus());
    }

    @Test @WithMockUser(username = "reviewer", roles = "REVIEWER")
    void reviewerCannotDelete() throws Exception {
        Dataset d = inReview("user");
        mvc.perform(post("/datasets/" + d.getId() + "/delete").with(csrf())).andExpect(status().isForbidden());
    }
}
