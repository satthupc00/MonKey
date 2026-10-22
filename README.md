# MonKey – bàn phím VNI cho Galaxy Z Fold 7

Bàn phím Android gõ tiếng Việt kiểu VNI. Khi mở máy, bàn phím tách thành hai nửa hình quạt quanh hai ngón cái; có chế độ một tay và tự đổi theo tư thế gập/mở.

## Cài đặt
1. Tải `MonKey-vX.Y.Z.apk` ở mục **Releases** và cài.
2. Mở app MonKey → **1. Bật bàn phím MonKey** → bật "MonKey VNI".
3. **2. Chọn MonKey làm bàn phím**.

## Mở dự án
Mở thư mục này bằng Android Studio (File → Open), chờ Gradle sync rồi bấm Run.

| File | Nội dung |
|---|---|
| `app/src/main/java/com/mondiro/monkey/VniEngine.kt` | Bộ gõ VNI |
| `.../Layouts.kt` | Vị trí, góc xoay của từng phím (vòng cung, một tay, thường) |
| `.../KeyboardView.kt` | Vẽ phím, xử lý chạm, vuốt, hiệu ứng chuyển chế độ |
| `.../MonKeyIME.kt` | Dịch vụ bàn phím, nhận biết gập/mở |
| `.../SettingsActivity.kt` | Màn hình cài đặt |
| `.../Updater.kt` | Tự cập nhật từ GitHub Releases |

## Phát hành bản mới
Tăng `versionCode` / `versionName` trong `app/build.gradle.kts`, thêm mục mới vào `CHANGELOG.md`, rồi push. GitHub Actions tự build APK và tạo Release; app thấy bản mới khi bấm "Kiểm tra bản cập nhật".

### Khoá ký (bắt buộc để tự cập nhật được)
Mọi bản APK phải ký cùng một khoá thì mới cài đè lên nhau. Khoá **không** để trong repo (repo công khai; bàn phím thấy mọi thứ bạn gõ nên khoá lộ ra là nguy hiểm). Thêm 2 secret ở GitHub → repo → Settings → Secrets and variables → Actions:
- `MONKEY_KEYSTORE`: file keystore mã hoá base64
- `MONKEY_KEY_PASSWORD`: mật khẩu keystore (alias `monkey`)

Chưa có secrets thì APK vẫn build được nhưng ký bằng khoá debug tạm (mỗi lần build khác nhau → phải gỡ app cũ trước khi cài).

Created by Mondiro
