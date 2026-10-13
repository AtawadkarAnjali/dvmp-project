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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Selenium UI tests (headless Chrome). Skip with: mvn test -DskipUi=true
 * Every step waits explicitly for the next page/element, so the tests are stable on slow machines (Jenkins).
 */
@DisabledIfSystemProperty(named = "skipUi", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalUiTest {

    @LocalServerPort int port;
    static WebDriver driver;
    static WebDriverWait wait;

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
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    @AfterAll
    static void stopBrowser() { if (driver != null) driver.quit(); }

    @BeforeEach
    void reset() { driver.manage().deleteAllCookies(); }

    // ---------- helpers (all wait for the element before using it) ----------
    private String url(String path) { return "http://localhost:" + port + path; }

    private WebElement byId(String id) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));
    }

    private void type(String id, String text) { byId(id).sendKeys(text); }

    private void attach(String id, Path file) {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id(id)))
            .sendKeys(file.toAbsolutePath().toString());
    }

    private void click(String id) {
        wait.until(ExpectedConditions.elementToBeClickable(By.id(id))).click();
    }

    private void waitText(String id, String expected) {
        wait.until(ExpectedConditions.textToBe(By.id(id), expected));
    }

    /** Signs in and waits until either the dashboard or the error message is shown. */
    private void login(String user, String pass) {
        driver.get(url("/login"));
        type("username", user);
        type("password", pass);
        click("login-btn");
        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.id("whoami")),
                ExpectedConditions.presenceOfElementLocated(By.id("login-error"))));
    }

    private void logout() {
        click("logout");
        byId("login-btn");          // login page is back
    }

    private Path sampleCsv() throws IOException {
        Path p = Files.createTempFile("sample-", ".csv");
        Files.writeString(p, "id,value\n1,10\n2,20\n");
        return p;
    }

    /** Logged in as USER: uploads a dataset with a file, waits for its page, returns the unique name. */
    private String uploadDataset(String prefix) throws IOException {
        String name = prefix + "-" + System.currentTimeMillis();
        driver.get(url("/datasets/new"));
        type("name", name);
        type("subtitle", "Uploaded by an automated test");
        type("description", "created by selenium to verify the upload journey end to end");
        type("tags", "Selenium, Test");
        attach("file", sampleCsv());
        click("save");
        waitText("title", name);    // dataset page is shown
        return name;
    }

    // ---------- journeys ----------
    @Test
    void journey1_wrongPasswordIsRejected() {
        login("user", "wrong");
        assertTrue(byId("login-error").isDisplayed());
    }

    @Test
    void journey2_userLoginShowsDashboard() {
        login("user", "user123");
        assertEquals("user", byId("whoami").getText());
        assertEquals("USER", byId("role-badge").getText());
        assertTrue(Integer.parseInt(byId("total-count").getText()) >= 2);
        assertFalse(driver.findElements(By.id("new-dataset")).isEmpty());
        assertEquals("TEST", byId("env").getText());
    }

    @Test
    void journey3_userUploadsDatasetWithFile() throws IOException {
        login("user", "user123");
        String name = uploadDataset("upload");
        assertEquals(name, byId("title").getText());
        assertEquals("DRAFT", byId("status").getText());
        assertEquals("v1", byId("current-version").getText());
        assertEquals("Uploaded by an automated test", byId("subtitle").getText());
        assertTrue(byId("preview-summary").getText().contains("2 columns"));
        assertTrue(byId("preview-table").getText().contains("integer"));
        WebElement download = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a.download")));
        assertTrue(download.getText().contains(".csv"));
        assertEquals(2, driver.findElements(By.cssSelector(".hero a.tag")).size());
    }

    @Test
    void journey4_searchDataset() throws IOException {
        login("user", "user123");
        String name = uploadDataset("search");
        driver.get(url("/"));
        type("q", name);
        click("search-btn");
        wait.until(ExpectedConditions.urlContains("q="));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("dataset-table"), name));
        assertTrue(byId("dataset-table").getText().contains(name));
    }

    @Test
    void journey5_validationErrorsForEmptyForm() {
        login("user", "user123");
        driver.get(url("/datasets/new"));
        click("save");
        assertTrue(byId("name-error").getText().contains("Name is required"));
        assertTrue(byId("file-error").getText().contains("attach"));
    }

    @Test
    void journey6_fullWorkflowAcrossRoles() throws IOException {
        login("user", "user123");
        uploadDataset("flow");
        String datasetUrl = driver.getCurrentUrl();

        // upload a second version
        type("notes", "cleaned nulls");
        attach("version-file", sampleCsv());
        click("add-version");
        waitText("current-version", "v2");

        // submit for review; the owner cannot approve
        click("submit-review");
        waitText("status", "IN_REVIEW");
        assertTrue(driver.findElements(By.id("approve")).isEmpty());
        logout();

        // reviewer: no upload button, can approve
        login("reviewer", "reviewer123");
        assertTrue(driver.findElements(By.id("new-dataset")).isEmpty());
        driver.get(datasetUrl);
        click("approve");
        waitText("status", "APPROVED");
        logout();

        // admin sees delete, user does not
        login("admin", "admin123");
        driver.get(datasetUrl);
        byId("title");
        assertFalse(driver.findElements(By.id("delete")).isEmpty());
        logout();
        login("user", "user123");
        driver.get(datasetUrl);
        byId("title");
        assertTrue(driver.findElements(By.id("delete")).isEmpty());
    }

    @Test
    void journey7_reviewerBlockedFromUploadPage() {
        login("reviewer", "reviewer123");
        driver.get(url("/datasets/new"));
        byId("denied-title");
        assertTrue(driver.getPageSource().contains("Access denied"));
    }

    @Test
    void journey8_guidePageShowsRoleRules() {
        driver.get(url("/guide"));
        assertTrue(byId("guide-title").getText().contains("Portal guide"));
        assertTrue(byId("role-reviewer").getText().contains("Approve"));
        assertTrue(byId("matrix").getText().contains("Delete dataset"));
    }
}
