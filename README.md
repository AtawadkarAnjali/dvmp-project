# Dockerized Dataset Version Management Portal

Spring Boot + Thymeleaf app with Jenkins CI/CD, Selenium tests, Docker and Ansible.

## Prerequisites
JDK 17, Maven 3.9+, Git, Chrome (for Selenium), Docker, Jenkins, Ansible (+ `ansible-galaxy collection install community.docker`).

## Run locally  (Task 3 / 6)
```bash
mvn spring-boot:run          # http://localhost:9090  (port 8080 is kept free for Jenkins)
```
If you ran an older version of this project, delete the old `data` and `uploads` folders first (so the new sample datasets are created).

### Login accounts and what each role can do
| Username | Password | Role | Permissions |
|---|---|---|---|
| user / user2 | user123 | USER | Upload datasets (file required), upload new versions, edit and submit **their own** datasets |
| reviewer | reviewer123 | REVIEWER | View/download everything, approve or reject datasets **in review** (with a comment); cannot upload |
| admin | admin123 | ADMIN | Everything, including edit and delete of any dataset |

Rules: a dataset cannot be reviewed by its own uploader; uploading a new version of an APPROVED dataset sends it back to DRAFT; datasets IN_REVIEW are locked.
**Guide page:** `/guide` lists every role's permissions, the dataset lifecycle, file rules and how the Kaggle-style *usability score* is calculated.
Dataset pages show subtitle, tags, license, description, a CSV/TSV data preview (column types + row count), per-version downloads and checksums.
Uploaded files are stored in `./uploads` with a SHA-256 checksum per version (download from the version table).

## Tests  (Task 9)
```bash
mvn test                  # unit/security tests + Selenium (headless Chrome)
mvn test -DskipUi=true    # skip Selenium
```
Failure screenshots land in `target/screenshots/`.

## Git  (Tasks 4-6)
```bash
git init && git add . && git commit -m "chore: initial project skeleton"
git branch -M main && git remote add origin <your-repo-url> && git push -u origin main
git checkout -b develop && git push -u origin develop
git checkout -b feature/search-improvements   # ...work, commit, push, open PR, merge
git tag -a v1.0 -m "MVP release" && git push --tags
```
Conflict demo: edit the same line of `README.md` on two branches, merge both, resolve the markers, commit.

## Jenkins  (Tasks 7, 8, 10, 12)
1. `docker run -d -p 8080:8080 -p 50000:50000 -v jenkins_home:/var/jenkins_home jenkins/jenkins:lts` (or install natively; use another port than the app).
2. Plugins: Git, Pipeline, JUnit, Docker Pipeline. Tools: Maven `Maven3`, JDK `JDK17`.
3. Credentials: `dockerhub-creds` (Docker Hub username + access token).
4. **Task 7 (freestyle):** new job -> Git repo URL -> Poll SCM `H/5 * * * *` -> build step `mvn clean package` -> archive `target/dvmp.jar`.
5. **Task 8/10/12:** new *Pipeline script from SCM* job -> path `Jenkinsfile`. Edit `DOCKER_USER` in the file. Run "Build with Parameters".
   - `DEPLOY_TARGET=tomcat` builds `dvmp.war` and copies it to Tomcat; `ENV` is the parameterized setting (shown in the page footer for Docker runs).
   - **Task 10 demo:** change an assertion (e.g. `"v2"` -> `"v9"` in `PortalUiTest`), push: Test stage fails, deploy is skipped. Fix, push, green.

## Docker  (Tasks 11, 12)
```bash
mvn clean package -DskipTests
docker build -t dvmp:1.0 .
docker run -d --name dvmp -p 8081:8080 -e SPRING_PROFILES_ACTIVE=dev dvmp:1.0
docker ps && docker logs dvmp
docker stop dvmp && docker start dvmp && docker rm -f dvmp
```
Open http://localhost:8081 (log in with the accounts above). Jenkins needs access to Docker (`usermod -aG docker jenkins`, restart Jenkins).

## Ansible  (Tasks 13, 14)
```bash
cd ansible
ansible-playbook -i inventory.ini site.yml --ask-become-pass                 # configure node
ansible-playbook -i inventory.ini site.yml --ask-become-pass                 # run again: changed=0 (idempotency)
ansible-playbook -i inventory.ini deploy.yml --ask-become-pass \
  -e dvmp_image=<user>/dvmp:2 -e dvmp_previous_image=<user>/dvmp:1           # deploy + health check
```
**Rollback demo:** deploy an image that fails the health check (e.g. one built with a bad `SPRING_PROFILES_ACTIVE` or a deliberately broken build); the `rescue` block redeploys `dvmp_previous_image`.

## Layout
`src/` app + tests - `Dockerfile` - `Jenkinsfile` - `ansible/` - `docs/PLANNING.md` (Tasks 1-3 drafts)
