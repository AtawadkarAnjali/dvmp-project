package com.devops.dvmp;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

/** Selenium UI tests (headless Chrome). Skip with: mvn test -DskipUi=true */
@DisabledIfSystemProperty(named = "skipUi", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalUiTest {

    @LocalServerPort int port;
    static WebDriver driver;

    @RegisterExtension
    TestWatcher screenshotOnFailure = new TestWatcher() {
        @Override public void testFailed(ExtensionContext ctx, Throwable cause) {
            try {
                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Path dir = Path.of("target", "screenshots");
                Files.createDirectories(dir);
                Files.copy(src.toPath(), dir.resolve(ctx.getRequiredTestMethod().getName() + ".png"),
                           StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) { }
        }
    };

    @BeforeAll
    static void startBrowser() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions o = new ChromeOptions();
        o.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1280,1000");
        driver = new ChromeDriver(o);
    }

    @AfterAll
    static void stopBrowser() { if (driver != null) driver.quit(); }

    @BeforeEach
    void reset() { driver.manage().deleteAllCookies(); }

    // ---------- helpers ----------
    private String url(String path) { return "http://localhost:" + port + path; }

    private void login(String user, String pass) {
        driver.get(url("/login"));
        driver.findElement(By.id("username")).sendKeys(user);
        driver.findElement(By.id("password")).sendKeys(pass);
        driver.findElement(By.id("login-btn")).click();
    }

    private void logout() { driver.findElement(By.id("logout")).click(); }

    private Path sampleCsv() throws IOException {
        Path p = Files.createTempFile("sample-", ".csv");
        Files.writeString(p, "id,value\n1,10\n2,20\n");
        return p;
    }

    /** Logged in as USER: uploads a dataset with a file; returns its unique name. */
    private String uploadDataset(String prefix) throws IOException {
        String name = prefix + "-" + System.currentTimeMillis();
        driver.get(url("/datasets/new"));
        driver.findElement(By.id("name")).sendKeys(name);
        driver.findElement(By.id("subtitle")).sendKeys("Uploaded by an automated test");
        driver.findElement(By.id("description")).sendKeys("created by selenium to verify the upload journey end to end");
        driver.findElement(By.id("tags")).sendKeys("Selenium, Test");
        driver.findElement(By.id("file")).sendKeys(sampleCsv().toAbsolutePath().toString());
        driver.findElement(By.id("save")).click();
        return name;
    }

    // ---------- journeys ----------
    @Test
    void journey1_wrongPasswordIsRejected() {
        login("user", "wrong");
        assertTrue(driver.findElement(By.id("login-error")).isDisplayed());
    }

    @Test
    void journey2_userLoginShowsDashboard() {
        login("user", "user123");
        assertEquals("user", driver.findElement(By.id("whoami")).getText());
        assertEquals("USER", driver.findElement(By.id("role-badge")).getText());
        assertTrue(Integer.parseInt(driver.findElement(By.id("total-count")).getText()) >= 2);
        assertFalse(driver.findElements(By.id("new-dataset")).isEmpty());
        assertEquals("TEST", driver.findElement(By.id("env")).getText());
    }

    @Test
    void journey3_userUploadsDatasetWithFile() throws IOException {
        login("user", "user123");
        String name = uploadDataset("upload");
        assertEquals(name, driver.findElement(By.id("title")).getText());
        assertEquals("DRAFT", driver.findElement(By.id("status")).getText());
        assertEquals("v1", driver.findElement(By.id("current-version")).getText());
        assertFalse(driver.findElements(By.cssSelector("a.download")).isEmpty());
        assertTrue(driver.findElement(By.cssSelector("a.download")).getText().contains(".csv"));
        assertEquals("Uploaded by an automated test", driver.findElement(By.id("subtitle")).getText());
        assertTrue(driver.findElement(By.id("preview-summary")).getText().contains("2 columns"));
        assertTrue(driver.findElement(By.id("preview-table")).getText().contains("integer"));
        assertEquals(2, driver.findElements(By.cssSelector(".hero a.tag")).size());
    }

    @Test
    void journey4_searchDataset() throws IOException {
        login("user", "user123");
        String name = uploadDataset("search");
        driver.get(url("/"));
        driver.findElement(By.id("q")).sendKeys(name);
        driver.findElement(By.id("search-btn")).click();
        assertTrue(driver.findElement(By.id("dataset-table")).getText().contains(name));
    }

    @Test
    void journey5_validationErrorsForEmptyForm() {
        login("user", "user123");
        driver.get(url("/datasets/new"));
        driver.findElement(By.id("save")).click();
        assertTrue(driver.findElement(By.id("name-error")).getText().contains("Name is required"));
        assertTrue(driver.findElement(By.id("file-error")).getText().contains("attach"));
    }

    @Test
    void journey6_fullWorkflowAcrossRoles() throws IOException {
        login("user", "user123");
        uploadDataset("flow");
        String datasetUrl = driver.getCurrentUrl();

        // new version
        driver.findElement(By.id("notes")).sendKeys("cleaned nulls");
        driver.findElement(By.id("version-file")).sendKeys(sampleCsv().toAbsolutePath().toString());
        driver.findElement(By.id("add-version")).click();
        assertEquals("v2", driver.findElement(By.id("current-version")).getText());

        // submit for review, then the owner cannot approve
        driver.findElement(By.id("submit-review")).click();
        assertEquals("IN_REVIEW", driver.findElement(By.id("status")).getText());
        assertTrue(driver.findElements(By.id("approve")).isEmpty());
        logout();

        // reviewer: cannot upload, can approve
        login("reviewer", "reviewer123");
        assertTrue(driver.findElements(By.id("new-dataset")).isEmpty());
        driver.get(datasetUrl);
        driver.findElement(By.id("approve")).click();
        assertEquals("APPROVED", driver.findElement(By.id("status")).getText());
        logout();

        // admin sees delete, user does not
        login("admin", "admin123");
        driver.get(datasetUrl);
        assertFalse(driver.findElements(By.id("delete")).isEmpty());
        logout();
        login("user", "user123");
        driver.get(datasetUrl);
        assertTrue(driver.findElements(By.id("delete")).isEmpty());
    }

    @Test
    void journey7_reviewerBlockedFromUploadPage() {
        login("reviewer", "reviewer123");
        driver.get(url("/datasets/new"));
        assertTrue(driver.getPageSource().contains("Access denied"));
    }

    @Test
    void journey8_guidePageShowsRoleRules() {
        driver.get(url("/guide"));
        assertTrue(driver.findElement(By.id("guide-title")).getText().contains("Portal guide"));
        assertTrue(driver.findElement(By.id("role-reviewer")).getText().contains("Approve"));
        assertTrue(driver.findElement(By.id("matrix")).getText().contains("Delete dataset"));
    }
}
