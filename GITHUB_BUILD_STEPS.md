# Build the APK on GitHub (no Android Studio, no local install)

## A. One-time setup (about 10 minutes)
1. **Create the repository** - github.com > **+** > **New repository** > name `TeSysTeraLab` > **Private** > do NOT tick "Add a README" > **Create repository**.
2. **Unzip** the project. Open the folder: you must see `settings.gradle.kts`, `build.gradle.kts`, `app` and `.github` **directly inside** it
   (not inside another folder). In Windows Explorer turn on **View > Show > Hidden items** if `.github` is not visible.
3. **Upload**: on the empty repo page click **uploading an existing file**, press Ctrl+A inside the unzipped folder, drag everything into the browser, wait until it finishes, then **Commit changes** (branch `main`).
4. **Check** `.github/workflows/build-apk.yml` exists. If it does not (some browsers skip dot-folders): **Add file > Create new file**, type the name `.github/workflows/build-apk.yml`, paste the contents of that file, **Commit**.

## B. Run the build
It starts automatically on every push to `main`. Manually: **Actions** tab > **Build TeSys Tera Lab APK** > **Run workflow**.
First run takes 4-8 minutes.

## C. Install on the phone
Open the finished run > **Artifacts** > **TeSysTeraLab-debug-apk** > unzip > copy the APK to the phone > open it > allow **Install unknown apps** > **Install**.
Plug the USB-RS485 cable through an OTG adapter; tap **OK** on the USB permission prompt.

## D. If the build fails
Open the red step > copy the last 40 lines of the log and send them to me.

| Symptom | Cause | Fix |
|---|---|---|
| `Could not resolve com.github.mik3y...` | JitPack temporarily down | Re-run failed jobs |
| `Unresolved reference` in a .kt file | code error (never compiled yet) | send me the log |
| `No such file ... settings.gradle.kts` | files uploaded inside an extra folder | move files to repo root |
| No run starts | workflow file missing | step A.4 |
