# Nhật ký thay đổi MonKey

## v1.7.1
**Quan trọng – bản Android này là app MỚI**
- App đã được đăng ký chính chủ với Google (Android Developer Console) để không còn bị Play Protect chặn khi cập nhật.
- Vì vậy app đổi sang mã app mới `com.mondiro.vnikey`, **không cài đè lên bản cũ được** (khoá ký giữ nguyên).
- Cách lên đời: cài `MonKey-v1.7.1.apk` → vào Cài đặt → Bàn phím, bật MonKey mới và chọn làm bàn phím mặc định → kiểm tra → gỡ bản cũ.
- Từ bản này trở đi, cập nhật sẽ cài đè bình thường.

## v1.7.0
- Chữ trên phím dùng font Roboto Regular (giống Laban Key) đóng gói sẵn trong app: nét thanh, cao, không còn bị đậm và lùn theo font Samsung.
- Màn ngoài: bỏ dải nút thu gọn riêng (đã có nút ⌄ của hệ thống), bàn phím thấp lại.

## v1.6.0
- Bỏ nút thu gọn tròn ở bàn phím một tay (còn Đổi bên / Hai tay / Dán / Cài đặt).
- Thêm "Nhích bàn phím một tay vào trong" (0–200dp) trong Cài đặt → Một tay.

## v1.5.0
- Bỏ nút thu gọn tròn ở giữa bàn phím vòng cung (dùng nút ⌄ trên thanh điều hướng).
- Thêm "Bề rộng phím một tay" (60–100%) trong Cài đặt → Một tay, chỉnh riêng với bàn phím tách đôi.

## v1.4.1
- Sửa thanh điều hướng dưới bàn phím bị trắng ở màn trong: giờ cùng màu nền bàn phím, nút thu gọn ⌄ hiện rõ.

## v1.4.0
**Tự bỏ dấu đúng chỗ, thu hẹp bàn phím tách đôi**

- Tự bỏ dấu đúng chỗ: gõ dấu sau cả chữ vẫn đúng (toan1 → toán, nguoi72 → người), dấu tự dời khi gõ tiếp (hoa2n → hoàn). Chọn kiểu cũ (hòa, thúy) hoặc kiểu mới (hoà, thuý) trong Cài đặt.
- Thêm "Bề rộng phím" (60–100%, mặc định 85%) để thu hẹp mỗi nửa bàn phím tách đôi, ngón cái đỡ phải với xa.
- Bỏ tự viết hoa đầu câu.
- Mặc định mới: độ cong 5, cỡ phím 40, nhích vào trong 39, độ rung 88 (tự áp khi cập nhật).
- Thêm nút "Đặt lại mặc định" trong Cài đặt.

## v1.3.0
**Bàn phím ký hiệu 2 trang giống Laban**

- Phím ?123 mở trang ký hiệu 1: ~ ` | • √ π ÷ × ¶ Δ / @ # $ % & - + ( ) / / * " ' : ; ! ? và _ . cạnh phím cách.
- Phím =\< (vị trí shift) lật sang trang 2: £ ¢ € ¥ ^ ° = { } … / \ © ® ™ % [ ] và < > cạnh phím cách; phím ?123 để quay lại trang 1, ABC để về bàn phím chữ.
- Dấu phẩy và dấu chấm cạnh phím cách giữ nguyên (không còn đổi thành { }).
- Màn ngoài: nút thu gọn bàn phím chuyển xuống dải mỏng phía dưới, phím cách rộng hơn.
- Màn trong: mặc định nhích hai nửa bàn phím vào trong 28dp (chỉnh trong Cài đặt → Nhích vào trong).

## v1.2.0
**Tông màu Laban, chữ dày hơn, thu gọn bàn phím**

- Tông màu giống Laban Key: nền xanh đen, phím xanh xám, phím chức năng tối hơn, chữ trắng.
- Chữ trên phím to và đậm hơn (nhất là màn ngoài); icon shift / xoá đặc như Laban.
- Màn trong: thêm phím dấu phẩy (trái) và dấu chấm (phải) ở hàng dưới.
- Màn trong: thêm "Nhích vào trong" trong cài đặt để đẩy hai nửa bàn phím vào giữa.
- Sửa lỗi vuốt phím cách không chuyển sang bàn phím một tay (phím cách nghiêng làm vuốt bị lệch chéo).
- Màn ngoài: gộp cài đặt vào phím dấu phẩy (giữ để mở cài đặt), phím cách rộng hơn; giữ dấu chấm để dán.
- Thêm phím thu gọn bàn phím (màn ngoài: cạnh phím cách; màn trong: nút tròn ở giữa; một tay: nút tròn bên cạnh).

## v1.1.0
**Phím phụ, dán clipboard, rung, xoá nhanh**

- Giữ phím chữ một chút để gõ ký tự phụ (giống Laban Key): a@ s# d$ f% g_ h- j+ k( l) · z* x" c' v: b; n! m?. Ký tự phụ hiện nhỏ ở góc trên phải mỗi phím.
- Chữ trên phím đậm, to và trắng rõ hơn.
- Thêm nút dán (paste) nội dung đã copy: ở giữa bàn phím vòng cung, cạnh bàn phím một tay và hàng dưới bàn phím thường.
- Sửa rung: dùng bộ rung trực tiếp, thêm thanh chỉnh độ mạnh rung (0 = tắt).
- Giữ phím xoá: xoá nhanh dần, giữ lâu (~2 giây) sẽ xoá từng từ.
- Độ cong = 0 giờ phẳng hoàn toàn; cài đặt được áp lại mỗi lần bàn phím hiện lên.

## v1.0.0
**Bản đầu tiên – bàn phím vòng cung cho Z Fold 7**

- Gõ tiếng Việt kiểu VNI (1 sắc · 2 huyền · 3 hỏi · 4 ngã · 5 nặng · 6 mũ · 7 móc · 8 trăng · 9 đ · 0 xoá dấu). Dấu đặt lên nguyên âm gần con trỏ nhất.
- Mở máy: bàn phím vòng cung hai tay, mỗi nửa xếp hình quạt quanh ngón cái, có hàng số.
- Nhân đôi phím giữa (B H Y 6 / V G T 5) có viền đỏ, bật/tắt được.
- Chế độ một tay: vuốt trái/phải trên phím cách để thu về một bên; vuốt sang bên kia để đổi bên; vuốt lên để về hai tay. Có nút tròn "Đổi bên" / "Hai tay". Nhớ bên đã dùng lần trước.
- Hiệu ứng trượt phím mượt khi chuyển chế độ.
- Tự đổi theo tư thế máy: gập → bàn phím thường; gập nửa → bàn phím thường full; mở phẳng → vòng cung.
- Cài đặt: độ cong, cỡ phím, nâng bàn phím, cong nhẹ một tay, nâng bàn phím một tay, rung.
- Nút "Kiểm tra bản cập nhật" + tự cập nhật từ GitHub.
