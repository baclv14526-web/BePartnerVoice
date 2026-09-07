# 🎤 BePartner Voice Control

Ứng dụng hỗ trợ tài xế BeBike điều khiển ứng dụng **BePartner** bằng **giọng nói**, không cần chạm màn hình — an toàn khi lái xe.

---

## ✨ Tính năng

| Tính năng | Mô tả |
|-----------|-------|
| **Nhận lệnh giọng nói** | Tiếng Việt (Google STT), hoạt động offline khi đã cache |
| **Accessibility Service** | Tự động nhấn nút trong BeBike Partner |
| **Floating HUD** | Nút mic nhỏ, kéo thả vị trí, hiển thị trạng thái |
| **Rung phản hồi** | Rung ngắn = thành công, rung đôi = không tìm thấy nút |
| **Tự khởi động** | Tự bật khi điện thoại khởi động lại |
| **Chạy nền** | Foreground service, không bị Android kill |

---

## 📱 Yêu cầu

- **Thiết bị:** Realme 2 (hoặc bất kỳ Android 9+)
- **OS:** Android 9 (Pie / API 28) trở lên
- **Google App:** Cần cài Google app cho Speech Recognition
- **Internet:** Lần đầu cần mạng để cache mô hình tiếng Việt

---

## 🚀 Cài đặt

### Bước 1: Build APK

```bash
# Clone hoặc copy source vào Android Studio
# File > Open > chọn thư mục BePartnerVoice
# Build > Generate Signed APK hoặc dùng debug build

./gradlew assembleDebug
# APK xuất tại: app/build/outputs/apk/debug/app-debug.apk
```

### Bước 2: Cài APK vào Realme 2

```bash
adb install app-debug.apk
# Hoặc copy APK sang điện thoại, mở file manager cài thủ công
```

### Bước 3: Cấu hình trong app

Mở **BePartner Voice** → làm theo 3 bước trên màn hình:

1. **Accessibility** → Cài đặt → Hỗ trợ tiếp cận → Ứng dụng đã cài → **BePartner Voice Control** → BẬT
2. **Overlay** → Cho phép hiển thị trên ứng dụng khác → BẬT
3. **Microphone** → Cấp quyền khi được hỏi

### Bước 4: Xác định package name BeBike

> ⚠️ **Quan trọng:** Package name của BeBike Partner có thể khác nhau theo phiên bản

```bash
# Kết nối điện thoại qua USB, chạy lệnh:
adb shell pm list packages | grep -i be
adb shell pm list packages | grep -i bike
adb shell pm list packages | grep -i partner
```

Sau đó cập nhật file `res/xml/accessibility_service_config.xml`:
```xml
android:packageNames="com.your.actual.bebike.package"
```

---

## 🎤 Danh sách lệnh giọng nói

| Nói | Nút được nhấn |
|-----|---------------|
| **"Đã đến điểm đón"** / "Đã đến rồi" | `[Đã đến]` |
| **"Bắt đầu chuyến đi"** / "Khởi hành" | `[Bắt đầu chuyến]` |
| **"Trả khách"** / "Hoàn thành" | `[Trả khách]` / `[Complete]` |
| **"Chấp nhận chuyến"** / "Nhận chuyến" | `[Chấp nhận]` |
| **"Từ chối"** / "Bỏ qua" | `[Từ chối]` |
| **"Online"** / "Sẵn sàng" | Bắt đầu nhận chuyến |
| **"Offline"** / "Nghỉ thôi" | Dừng nhận chuyến |
| **"Báo cáo"** | `[Báo cáo]` |

---

## 🔧 Tùy chỉnh nếu nút không được nhấn

### Vấn đề: App không tìm thấy nút trong BeBike

Nguyên nhân: BeBike dùng text khác với danh sách trong code.

**Cách debug:**

```bash
# Dùng UIAutomator viewer để xem text thực tế của nút:
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml
# Mở file XML, tìm các nút có clickable="true"
```

Sau đó thêm text vào `BePartnerAccessibilityService.kt`:

```kotlin
VoiceCommand.DA_DEN to listOf(
    "Đã đến",
    "TEXT_THỰC_TẾ_TRONG_BEBIKE",  // ← thêm vào đây
    ...
)
```

### Vấn đề: Nhận dạng giọng nói không chính xác

Thêm biến thể vào `VoiceCommandParser.kt`:
```kotlin
VoiceCommand.DA_DEN to listOf(
    "đã đến điểm đón",
    "DA_DEN_THEO_GIONG_CUA_BAN",  // ← cách Google nhận dạng giọng bạn
    ...
)
```

**Mẹo:** Nhìn vào HUD overlay xem Google nhận dạng gì (hiển thị text "❓ xxx")

---

## 🏗️ Kiến trúc

```
BePartner Voice
│
├── VoiceListenerService (Foreground Service)
│   ├── SpeechRecognizer (Google STT, tiếng Việt)
│   ├── VoiceCommandParser (khớp lệnh)
│   └── → gửi lệnh đến AccessibilityService
│
├── BePartnerAccessibilityService
│   ├── Nhận lệnh từ VoiceListenerService
│   ├── Duyệt view hierarchy của BeBike
│   ├── Tìm nút theo text/contentDescription/resourceId
│   └── Thực hiện performAction(ACTION_CLICK) hoặc GestureDescription
│
└── OverlayService
    ├── Floating HUD (TYPE_APPLICATION_OVERLAY)
    ├── Hiển thị trạng thái real-time
    └── Nút tắt/bật mic
```

---

## ⚡ Lưu ý quan trọng

- App **không can thiệp dữ liệu** của BeBike, chỉ nhấn nút
- Accessibility Service chỉ hoạt động khi **BeBike đang mở trên màn hình**
- Nếu BeBike cập nhật UI, có thể cần **cập nhật danh sách nhãn nút**
- Tắt **Battery Optimization** cho app: Cài đặt → Pin → Tối ưu hóa pin → BePartner Voice → Không tối ưu
- Trên Realme/OPPO/ColorOS: Tắt **Auto-clear** trong App Manager

---

## 📄 License

MIT – Dùng tự do cho mục đích cá nhân của tài xế BeBike.
