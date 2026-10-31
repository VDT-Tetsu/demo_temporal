# Tenant Onboarding Demo: Temporal + Java 21 + Spring Boot 3.5

Demo flow onboard tenant chạy trên Temporal workflow. Flow có đủ các trạng thái **SUCCESS / FAILED / PENDING (chờ trigger để chạy tiếp)**, kèm retry tự động, retry thủ công và rollback (Saga).
Các bước chỉ ghi log và sleep để giả lập. Mỗi bước có thể **lỗi ngẫu nhiên** theo tỉ lệ cấu hình.

## Flow

```
POST /api/tenants ──► PENDING
                        │
   VALIDATE_REQUEST ──► PROVISION_DATABASE ──► CREATE_ADMIN_USER ──► SETUP_BILLING      (IN_PROGRESS)
                        │ (bước nào lỗi quá N lần auto-retry)
                        ▼
                   WAITING_RETRY ── /retry ──► chạy lại bước đó
                        │ /abort hoặc hết timeout
                        ▼
                   COMPENSATING (rollback các bước đã xong, theo thứ tự ngược) ──► FAILED
                        
   SETUP_BILLING xong ──► WAITING_APPROVAL ── /approve ──► ACTIVATE_TENANT ──► SEND_WELCOME_EMAIL ──► SUCCESS
                                  │ /reject hoặc hết timeout
                                  ▼
                             COMPENSATING ──► FAILED
```

| Status | Ý nghĩa |
|---|---|
| `PENDING` | Đã tạo tenant, workflow chưa chạy bước nào |
| `IN_PROGRESS` | Đang chạy 1 bước (`currentStep`) |
| `WAITING_APPROVAL` | **Pending**: chờ admin gọi `approve` / `reject` |
| `WAITING_RETRY` | **Pending**: 1 bước đã lỗi quá số lần auto-retry, chờ gọi `retry` / `abort` |
| `COMPENSATING` | Đang rollback |
| `SUCCESS` | Onboard xong |
| `FAILED` | Bị reject / abort / timeout, đã rollback xong |

Trạng thái được lưu ở 2 nơi:
- **H2 (JPA)**: bảng `tenants` (trạng thái hiện tại) và `onboarding_events` (timeline). Workflow cập nhật qua `TenantStatusActivities`.
- **Temporal query** `getState()`: đọc trực tiếp từ workflow, qua API `/workflow-state`.

## Cấu trúc

```
src/main/java/com/demo/onboarding
├── workflow/   TenantOnboardingWorkflow(+Impl): orchestration, signal, query, saga
├── activity/   OnboardingActivities: các bước (log + random fail) và rollback
│               TenantStatusActivities: ghi status vào DB
├── service/    TenantOnboardingService: start workflow, gửi signal, query
├── api/        REST controller
├── domain/     Entity, repository, enum status/step
└── config/     OnboardingProperties
```

## Biến môi trường

Copy `.env.example` thành `.env` rồi điền giá trị. **Cả docker-compose và Spring Boot đều đọc file `.env` này.** Spring đọc qua `spring.config.import`, nên phải chạy app từ thư mục gốc project. Biến môi trường của OS sẽ ghi đè giá trị trong `.env`. Mọi biến đều có giá trị mặc định, nên thiếu `.env` thì app vẫn chạy.

| Biến | Mặc định | Mô tả |
|---|---|---|
| `SERVER_PORT` | `8081` | Port của app |
| `TEMPORAL_ADDRESS` | `localhost:7233` | Địa chỉ Temporal frontend |
| `TEMPORAL_NAMESPACE` | `default` | Namespace |
| `TEMPORAL_TASK_QUEUE` | `tenant-onboarding` | Task queue của worker |
| `ONBOARDING_FAILURE_RATE` | `0.3` | Tỉ lệ lỗi ngẫu nhiên mỗi lần chạy 1 bước (0 → 1) |
| `ONBOARDING_STEP_DELAY_MIN`, `ONBOARDING_STEP_DELAY_MAX` | `15s`, `30s` | Thời gian giả lập mỗi bước, mỗi lần chạy lấy ngẫu nhiên trong khoảng này |
| `ONBOARDING_ACTIVITY_MAX_ATTEMPTS` | `3` | Số lần auto-retry trước khi chuyển `WAITING_RETRY` |
| `ONBOARDING_APPROVAL_TIMEOUT` | `10m` | Thời gian chờ approve |
| `ONBOARDING_MANUAL_RETRY_TIMEOUT` | `30m` | Thời gian chờ retry/abort |
| `TEMPORAL_SERVER_VERSION`, `TEMPORAL_UI_VERSION`, `POSTGRES_VERSION` | `1.29.7`, `2.57.0`, `16` | Phiên bản image (docker-compose) |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | `temporal` | DB của Temporal (docker-compose) |
| `TEMPORAL_PORT` | `7233` | Port Temporal được expose ra máy (cả 2 phương án) |
| `TEMPORAL_UI_PORT` | `8080` | Port Temporal UI (docker-compose) |
| `TEMPORAL_RETENTION` | `168h` | Thời gian Temporal giữ workflow đã kết thúc (docker-compose; chỉ áp dụng khi tạo namespace lần đầu) |
| `TEMPORAL_UI_URL` | `http://localhost:8080` | Link Temporal UI trên FE |
| `H2_DB_PATH` | `./data/onboarding` | File H2 lưu tenant |
| `TEMPORAL_CLI_UI_PORT` | `8233` | Port Temporal UI (CLI dev server) |

> Tip demo: đặt `ONBOARDING_FAILURE_RATE=0.7` để dễ gặp `WAITING_RETRY`, hoặc `0` để luôn chạy suôn sẻ. Đặt `ONBOARDING_APPROVAL_TIMEOUT=1m` để thử trường hợp hết hạn approve.

## Chạy Temporal server: chọn 1 trong 2 phương án

Cả 2 phương án đều dùng port `7233`, nên **chỉ chạy 1 trong 2 cùng lúc**.

### Phương án 1: docker-compose (Temporal + PostgreSQL + UI)

```powershell
copy .env.example .env      # nếu chưa có
docker compose up -d
docker compose logs -f temporal   # đợi đến khi thấy server sẵn sàng (lần đầu mất khoảng 30s để tạo schema)
```
Temporal UI: http://localhost:8080

Tắt: `docker compose down` (thêm `-v` để xoá luôn data).

### Phương án 2: Temporal CLI dev server

Cài Temporal CLI bằng 1 trong các cách:
- Tải bản Windows tại https://temporal.download/cli/archive/latest?platform=windows&arch=amd64, giải nén rồi thêm `temporal.exe` vào PATH
- macOS: `brew install temporal`

Chạy:
```powershell
.\scripts\start-temporal-cli.ps1
# tương đương: temporal server start-dev --port 7233 --ui-port 8233 --db-filename temporal-dev.db
```
Temporal UI: http://localhost:8233. Data được lưu ở file `temporal-dev.db`; xoá file này để reset.

## Chạy app

```powershell
mvn spring-boot:run
# hoặc
mvn package -DskipTests; java -jar target/tenant-onboarding-0.0.1-SNAPSHOT.jar
```
H2 console: http://localhost:8081/h2-console (JDBC URL `jdbc:h2:file:./data/onboarding;AUTO_SERVER=TRUE`, user `sa`, không có password)

> Lưu ý: H2 lưu ra file `data/onboarding.mv.db` (đổi đường dẫn bằng `H2_DB_PATH`), nên restart app không mất dữ liệu và các workflow đang chạy sẽ tiếp tục bình thường. Khi muốn demo lại từ đầu, phải reset **cả hai** cùng lúc: xoá thư mục `data/` **và** xoá dữ liệu Temporal (xoá `temporal-dev.db` hoặc chạy `docker compose down -v`). Nếu chỉ xoá một bên, các workflow cũ sẽ không tìm thấy tenant và retry mãi ở bước cập nhật status.

## Giao diện web (FE)

Mở **http://localhost:8081** sau khi app đã chạy. FE là HTML + JS thuần trong `src/main/resources/static`, không cần build, và tự refresh mỗi 3 giây.

- **Thẻ thống kê** đếm số flow theo business status. Bấm vào thẻ để lọc danh sách; thẻ "Đang chờ trigger" gom cả `WAITING_APPROVAL` và `WAITING_RETRY`.
- **Danh sách flow** hiện business status cạnh execution status của Temporal để so sánh. Ví dụ: flow đang chờ duyệt có business status `Chờ duyệt` nhưng trên Temporal vẫn chỉ là `RUNNING`.
  - `NOT FOUND`: Temporal đã xoá workflow vì hết retention của namespace (mặc định 24h sau khi kết thúc). Dữ liệu trong DB vẫn còn.
  - `UNKNOWN`: không kết nối được Temporal.
- **Chi tiết flow** gồm:
  - Pipeline 7 bước, mỗi bước ở một trong các trạng thái: hoàn thành / đang chạy / chờ trigger / thất bại / đã rollback.
  - Timeline các lần chuyển trạng thái.
  - Link mở workflow trên Temporal UI.
- **Trigger:** các nút Approve / Reject / Retry / Abort chỉ bật khi flow ở đúng trạng thái. Ô nhập bên cạnh dùng cho người duyệt (approve) hoặc lý do (reject/abort). Khi flow đang chờ, có đồng hồ đếm ngược tới lúc hết hạn.
- **Nút "+ Tạo tenant"** để bắt đầu flow mới, có thể đặt tỉ lệ lỗi riêng cho tenant đó.

Link Temporal UI lấy từ biến `TEMPORAL_UI_URL`: dùng `http://localhost:8080` với docker-compose, `http://localhost:8233` với CLI dev server.

API dành riêng cho FE: `GET /api/config`, `GET /api/tenants/overview`, `GET /api/tenants/{id}/detail`.

## Kịch bản demo

Có thể dùng file `requests.http` hoặc PowerShell:

```powershell
$base = "http://localhost:8081/api/tenants"
$t = Invoke-RestMethod -Method Post -Uri $base -ContentType "application/json" `
      -Body '{"name":"Acme Corp","adminEmail":"admin@acme.io","plan":"PRO"}'
$id = $t.id

Invoke-RestMethod "$base/$id"                  # status, currentStep, message
Invoke-RestMethod "$base/$id/workflow-state"   # query trực tiếp workflow
Invoke-RestMethod "$base/$id/events"           # timeline
```

1. **SUCCESS**: đợi status `WAITING_APPROVAL` rồi gọi `Invoke-RestMethod -Method Post "$base/$id/approve"`. Flow chạy tiếp và kết thúc ở `SUCCESS`.
2. **FAILED do reject**: ở `WAITING_APPROVAL`, gọi `/reject`. Flow chuyển sang `COMPENSATING` rồi `FAILED`; log sẽ có các dòng `[ROLLBACK] ...`.
3. **PENDING do lỗi → retry**: với `ONBOARDING_FAILURE_RATE` cao, 1 bước lỗi quá số lần retry sẽ chuyển sang `WAITING_RETRY`. Gọi `/retry` để chạy lại bước đó.
4. **FAILED do abort**: ở `WAITING_RETRY`, gọi `/abort`. Các bước đã xong sẽ được rollback, rồi chuyển `FAILED`.
5. **FAILED do timeout**: không approve hay retry trong thời gian timeout.

Request tạo tenant có thể truyền thêm `"failureRate": 0.0 - 1.0` để ghi đè `ONBOARDING_FAILURE_RATE` cho riêng tenant đó. Ví dụ dùng `1.0` để chắc chắn rơi vào `WAITING_RETRY`.

### Tạo data test tự động

```powershell
.\scripts\seed-test-data.ps1
```
Script tạo 11 tenant, mỗi tenant có một kết quả khác nhau, và tự gửi approve/reject/retry/abort. Với mỗi bước 15–30s, script chạy khoảng 3–8 phút.

| Tenant | Kết quả |
|---|---|
| Công ty Minh Phát, Sao Mai Logistics | `SUCCESS` |
| Hải Đăng Tech | `SUCCESS`; nếu lỗi tới `WAITING_RETRY` thì script tự retry |
| Bình Minh Retail | Bị reject → rollback 3 bước → `FAILED` |
| Trường An Foods | Nếu lỗi tới `WAITING_RETRY` thì bị abort → rollback → `FAILED`; không lỗi thì `SUCCESS` |
| Kim Long Steel | Bị abort ngay bước đầu → `FAILED` |
| Ngọc Lan Spa, Thiên Long Media | Dừng ở `WAITING_APPROVAL` |
| Phúc Thịnh Build | Dừng ở `WAITING_RETRY` |
| Hoàng Gia Hotel, Đông Á Pharma | Tạo cuối cùng, không can thiệp: lúc script xong vẫn đang chạy |

Gọi signal sai trạng thái (ví dụ approve khi chưa tới `WAITING_APPROVAL`) sẽ trả về `409 Conflict`.

Trên Temporal UI, mở workflow `tenant-onboarding-<tên-tenant>-<id>` (ví dụ `tenant-onboarding-acme-corp-d270c483`) để xem event history: retry, signal, timer và compensation.

## Test

```powershell
mvn test
```
`TenantOnboardingWorkflowTest` dùng Temporal test server in-process (không cần server thật). Test bao các luồng: approve → SUCCESS, reject → rollback, timeout approval, lỗi → retry thủ công → SUCCESS, lỗi → abort → rollback.
