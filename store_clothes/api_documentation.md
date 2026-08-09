# Tài liệu API - Hệ thống Quản lý Bán hàng & Kho (Store Clothes)

Tài liệu này tổng hợp toàn bộ các API của dự án **Store Clothes**, bao gồm phương thức HTTP, đường dẫn, quyền truy cập (roles), các tham số và cấu trúc Request Body tương ứng.

> [!TIP]
> Bạn có thể import trực tiếp file [store_clothes_postman_collection.json](file:///C:/Users/ASUS/.gemini/antigravity-ide/brain/b144f02e-ebda-4a35-8bc6-973818bc07a7/store_clothes_postman_collection.json) vào Postman để kiểm thử nhanh chóng. Hãy nhớ thiết lập biến môi trường `base_url` (mặc định: `http://localhost:8080`) và `token` sau khi đăng nhập thành công.

---

## 1. Authentication (Xác thực & Tài khoản)
* **Base URL:** `/api/v1/auth`

| HTTP Method | Endpoint | Quyền (Role) | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | **PUBLIC** | Đăng nhập hệ thống bằng username/password để nhận access token và refresh token. |
| `POST` | `/api/v1/auth/refresh` | **PUBLIC** | Cấp lại access token mới từ refresh token qua query parameter. |
| `GET` | `/api/v1/auth/me` | **Authenticated** | Lấy thông tin tài khoản của người dùng đang đăng nhập dựa trên JWT token. |
| `PUT` | `/api/v1/auth/me/password` | **Authenticated** | Đổi mật khẩu của người dùng hiện tại (yêu cầu điền mật khẩu cũ). |
| `POST` | `/api/v1/tenants/register` | **PUBLIC** | Đăng ký tạo mới một cửa hàng (Tenant) trên hệ thống (SaaS). |

### Chi tiết Request Body

#### Đăng nhập (`POST /api/v1/auth/login`)
```json
{
  "username": "owner",
  "password": "password123"
}
```

#### Đổi mật khẩu (`PUT /api/v1/auth/me/password`)
```json
{
  "oldPassword": "password123",
  "newPassword": "newpassword123"
}
```

---

## 2. User Management (Quản lý Nhân viên)
* **Base URL:** `/api/v1/users`
* Các API này yêu cầu Header: `Authorization: Bearer <token>`

| HTTP Method | Endpoint | Quyền (Role) | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/users` | **ROLE_OWNER** | Tạo mới tài khoản nhân viên (ROLE_MANAGER, ROLE_CASHIER, ROLE_WAREHOUSE_STAFF). |
| `GET` | `/api/v1/users` | **OWNER, MANAGER** | Lấy danh sách nhân viên có phân trang, lọc theo keyword và vai trò. |
| `GET` | `/api/v1/users/{id}` | **OWNER, MANAGER** | Xem thông tin chi tiết nhân viên kèm theo thống kê đơn hàng và phiếu nhập đã tạo. |
| `PUT` | `/api/v1/users/{id}` | **ROLE_OWNER** | Cập nhật thông tin cơ bản & phân quyền (không cho đổi username/password qua đây). |
| `DELETE` | `/api/v1/users/{id}` | **ROLE_OWNER** | Xóa mềm tài khoản nhân viên (set `is_deleted=true`, khóa tài khoản và giải phóng unique constraint). |

### Chi tiết Request Body

#### Tạo nhân viên mới (`POST /api/v1/users`)
```json
{
  "username": "cashier_ngan",
  "password": "password123",
  "fullName": "Nguyễn Văn Ngân",
  "email": "cashier_ngan@example.com",
  "phone": "0912345678",
  "roleName": "ROLE_CASHIER"
}
```

#### Cập nhật thông tin & đổi quyền (`PUT /api/v1/users/{id}`)
```json
{
  "fullName": "Nguyễn Văn Ngân Sửa",
  "email": "cashier_ngan_edit@example.com",
  "phone": "0912345679",
  "status": "ACTIVE",
  "roleName": "ROLE_MANAGER"
}
```

---

## 3. Customer Management (Quản lý Khách hàng)
* **Base URL:** `/api/v1/customers`

| HTTP Method | Endpoint | Quyền (Role) | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/customers` | **OWNER, MANAGER, CASHIER** | Tạo mới thông tin khách hàng (điểm tích lũy & chi tiêu khởi tạo = 0). |
| `GET` | `/api/v1/customers` | **OWNER, MANAGER, CASHIER** | Danh sách khách hàng phân trang, lọc theo keyword (tên/SĐT) và lọc KH có điểm tích lũy. |
| `GET` | `/api/v1/customers/{id}` | **OWNER, MANAGER, CASHIER** | Xem thông tin chi tiết khách hàng và 10 đơn hàng gần nhất. |
| `PUT` | `/api/v1/customers/{id}` | **OWNER, MANAGER, CASHIER** | Cập nhật thông tin (name, phone, email, note). Không được thay đổi điểm và chi tiêu. |
| `DELETE` | `/api/v1/customers/{id}` | **OWNER, MANAGER** | Xóa mềm khách hàng (yêu cầu không có đơn hàng đang PENDING). |

### Chi tiết Request Body

#### Tạo khách hàng (`POST /api/v1/customers`)
```json
{
  "name": "Trần Văn Khách",
  "phone": "0987654321",
  "email": "khachhang@example.com",
  "note": "Khách hàng mua trực tiếp tại quầy"
}
```

---

## 4. Category Management (Quản lý Danh mục)
* **Base URL:** `/api/v1/categories`

| HTTP Method | Endpoint | Quyền (Role) | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/categories` | **OWNER, MANAGER** | Tạo danh mục sản phẩm (tối đa 2 cấp: Root -> Child). |
| `GET` | `/api/v1/categories` | **PUBLIC** | Lấy danh sách danh mục dạng phẳng (phân trang, lọc theo keyword). |
| `GET` | `/api/v1/categories/tree` | **PUBLIC** | Trả về toàn bộ danh mục sản phẩm dưới dạng cấu trúc cây (Root -> Children). |
| `GET` | `/api/v1/categories/{id}` | **PUBLIC** | Chi tiết danh mục kèm đếm số lượng sản phẩm đang `ACTIVE`. |
| `PUT` | `/api/v1/categories/{id}` | **OWNER, MANAGER** | Cập nhật tên, parentId hoặc trạng thái của danh mục (áp dụng Optimistic Lock). |
| `DELETE` | `/api/v1/categories/{id}` | **ROLE_OWNER** | Xóa mềm danh mục (chỉ khi không còn SP active và không còn danh mục con active). |

### Chi tiết Request Body

#### Tạo danh mục (`POST /api/v1/categories`)
```json
{
  "name": "Áo Thun Nam",
  "parentId": null, // null nếu là danh mục gốc
  "status": "ACTIVE"
}
```

---

## 5. Product & Variant Management (Quản lý Hàng hóa & Biến thể)
* **Base URL:** `/api/v1/products`

| HTTP Method | Endpoint | Quyền (Role) | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/products` | **OWNER, MANAGER** | Tạo sản phẩm gốc kèm danh sách các biến thể ban đầu (thủ công). |
| `POST` | `/api/v1/products/matrix` | **OWNER, MANAGER** | Sinh ma trận biến thể tự động theo Cartesian product (Colors x Sizes). SKU tự sinh. |
| `GET` | `/api/v1/products/{id}` | **OWNER, MANAGER, CASHIER, WAREHOUSE_STAFF** | Xem thông tin chi tiết sản phẩm và danh sách biến thể con. |
| `PUT` | `/api/v1/products/{id}` | **OWNER, MANAGER** | Cập nhật tên, mô tả hoặc danh mục của sản phẩm gốc (không cho sửa code). |
| `GET` | `/api/v1/products/variants/sku/{sku}` | **OWNER, MANAGER, CASHIER, WAREHOUSE_STAFF** | Tìm biến thể theo SKU. |
| `GET` | `/api/v1/products/variants/barcode/{barcode}` | **OWNER, MANAGER, CASHIER, WAREHOUSE_STAFF** | Tìm biến thể theo Barcode (phục vụ quét mã vạch bán hàng). |
| `POST` | `/api/v1/products/{productId}/variants` | **OWNER, MANAGER** | Thêm biến thể đơn lẻ vào sản phẩm đã có. |
| `PUT` | `/api/v1/products/variants/{id}` | **OWNER, MANAGER** | Cập nhật giá nhập, giá bán và trạng thái của biến thể. |
| `POST` | `/api/v1/products/variants/{id}/stock-adjustment` | **ROLE_OWNER** | Điều chỉnh tồn kho thủ công (đặt số lượng tồn kho mới, yêu cầu điền lý do). |
| `DELETE` | `/api/v1/products/{id}` | **OWNER, MANAGER** | Xóa mềm sản phẩm gốc và tất cả biến thể con (Cascade delete). |
| `DELETE` | `/api/v1/products/variants/{id}` | **OWNER, MANAGER** | Xóa mềm biến thể (yêu cầu tồn kho = 0 và không trong phiếu nhập DRAFT). |

### Chi tiết Request Body

#### Tạo sản phẩm thủ công (`POST /api/v1/products`)
```json
{
  "name": "Áo Khoác Gió Nam",
  "code": "AKG001",
  "description": "Áo khoác gió chống nước siêu nhẹ",
  "status": "ACTIVE",
  "variants": [
    {
      "sku": "AKG-DEN-M",
      "barcode": "8931234567890",
      "color": "Đen",
      "size": "M",
      "importPrice": 150000.00,
      "salePrice": 250000.00,
      "inventory": 50,
      "status": "ACTIVE"
    }
  ]
}
```

#### Tự động sinh ma trận biến thể (`POST /api/v1/products/matrix`)
```json
{
  "name": "Quần Tây Nam Slimfit",
  "description": "Quần tây chất liệu co giãn nhẹ",
  "colors": ["Đen", "Xám"],
  "sizes": ["29", "30", "31"],
  "baseImportPrice": 180000.00,
  "baseSalePrice": 320000.00
}
```

#### Điều chỉnh tồn kho (`POST /api/v1/products/variants/{id}/stock-adjustment`)
```json
{
  "newQuantity": 60,
  "reason": "Kiểm kê kho tháng 7 phát hiện lệch thừa 10 cái"
}
```

---

## 6. Supplier Management (Quản lý Nhà Cung Cấp)
* **Base URL:** `/api/v1/suppliers`

| HTTP Method | Endpoint | Quyền (Role) | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/suppliers` | **OWNER, MANAGER, WAREHOUSE_STAFF** | Tạo mới nhà cung cấp (mặc định công nợ khởi tạo = 0). |
| `GET` | `/api/v1/suppliers` | **OWNER, MANAGER, WAREHOUSE_STAFF** | Danh sách nhà cung cấp phân trang, lọc theo keyword và theo trạng thái đang nợ. |
| `GET` | `/api/v1/suppliers/{id}` | **OWNER, MANAGER, WAREHOUSE_STAFF** | Chi tiết nhà cung cấp kèm theo 5 phiếu nhập hàng gần nhất. |
| `PUT` | `/api/v1/suppliers/{id}` | **OWNER, MANAGER** | Cập nhật thông tin NCC (không cho sửa đổi công nợ trực tiếp qua đây). |
| `DELETE` | `/api/v1/suppliers/{id}` | **ROLE_OWNER** | Xóa mềm nhà cung cấp (chỉ khi công nợ = 0 và không có phiếu nhập DRAFT). |

### Chi tiết Request Body

#### Tạo nhà cung cấp (`POST /api/v1/suppliers`)
```json
{
  "name": "Xưởng may mặc Hà Nội",
  "phone": "0243123456",
  "email": "xuongmayhn@example.com",
  "address": "Đông Anh, Hà Nội",
  "taxCode": "0102030405"
}
```

---

## 7. Import Receipts (Phiếu Nhập Hàng)
* **Base URL:** `/api/v1/imports`

| HTTP Method | Endpoint | Quyền (Role) | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/imports` | **OWNER, MANAGER, WAREHOUSE_STAFF** | Tạo phiếu nhập mới ở trạng thái `DRAFT` (chưa cộng kho, chưa ghi nợ). |
| `POST` | `/api/v1/imports/{id}/complete` | **OWNER, MANAGER, WAREHOUSE_STAFF** | Hoàn thành phiếu nhập: cộng kho, ghi thẻ kho, cộng công nợ nhà cung cấp. |
| `POST` | `/api/v1/imports/{id}/cancel` | **OWNER, MANAGER** | Hủy phiếu nhập (chỉ khi đang ở trạng thái DRAFT). |
| `GET` | `/api/v1/imports/supplier/{supplierId}` | **OWNER, MANAGER, WAREHOUSE_STAFF** | Lấy danh sách phiếu nhập hàng lọc theo ID nhà cung cấp. |
| `GET` | `/api/v1/imports/{id}` | **OWNER, MANAGER, WAREHOUSE_STAFF** | Xem thông tin chi tiết phiếu nhập kèm danh sách hàng hóa và đơn giá. |
| `GET` | `/api/v1/imports` | **OWNER, MANAGER, WAREHOUSE_STAFF** | Danh sách phiếu nhập phân trang, lọc theo status, supplierId và khoảng thời gian. |
| `PUT` | `/api/v1/imports/{id}` | **OWNER, MANAGER, WAREHOUSE_STAFF** | Sửa đổi phiếu nhập đang ở dạng `DRAFT` (tự động xóa và lập lại chi tiết). |

### Chi tiết Request Body

#### Lập phiếu nhập hàng nháp (`POST /api/v1/imports`)
```json
{
  "supplierId": 1,
  "items": [
    {
      "variantId": 1,
      "quantity": 100,
      "importPrice": 150000.00
    }
  ],
  "paidAmount": 10000000.00, // Số tiền trả trước cho NCC
  "note": "Nhập hàng đợt đầu tháng 7"
}
```

---

## 8. POS Orders & Checkout (Hóa đơn & Bán hàng POS)
* **Base URL:** `/api/v1/orders`

| HTTP Method | Endpoint | Quyền (Role) | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/orders/checkout` | **OWNER, MANAGER, CASHIER** | Thanh toán giỏ hàng POS: trừ kho, ghi thẻ kho, tích điểm, sinh hóa đơn. |
| `POST` | `/api/v1/orders/{id}/cancel` | **OWNER, MANAGER** | Hủy hóa đơn đã thanh toán: hoàn tồn kho, ghi thẻ kho, trừ điểm tích lũy của KH. |
| `GET` | `/api/v1/orders` | **OWNER, MANAGER, CASHIER** | Xem danh sách hóa đơn phân trang, lọc theo status, customerId và thời gian. |

### Chi tiết Request Body

#### Thanh toán hóa đơn POS (`POST /api/v1/orders/checkout`)
```json
{
  "items": [
    {
      "variantId": 1,
      "quantity": 2
    }
  ],
  "paidAmount": 500000.00, // Số tiền khách đưa (phải >= tổng tiền hàng)
  "note": "Khách thanh toán bằng tiền mặt tại quầy"
}
```

---

## 9. Reports & Analytics (Báo cáo & Phân tích)
* **Base URL:** `/api/v1/reports`
* Tất cả các API báo cáo đều yêu cầu tài khoản đã được xác thực (`authenticated`).

| HTTP Method | Endpoint | Mô tả | Tham số truy vấn (Query Params) |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/reports/dashboard` | Lấy dữ liệu tổng quan cho Dashboard (doanh thu ngày, đơn hàng, top bán chạy). | *Không có* |
| `GET` | `/api/v1/reports/financial` | Báo cáo tài chính tổng hợp (doanh thu, lợi nhuận, chi phí nhập hàng). | `from` (ISO DateTime)<br>`to` (ISO DateTime) |
| `GET` | `/api/v1/reports/top-selling` | Top sản phẩm bán chạy nhất trong khoảng thời gian xác định. | `from` (ISO DateTime)<br>`to` (ISO DateTime)<br>`limit` (mặc định: 10) |
| `GET` | `/api/v1/reports/dead-stock` | Hàng tồn kho chậm (sản phẩm tồn kho cao nhưng không bán được trong X ngày). | `minInventory` (mặc định: 1)<br>`days` (mặc định: 90) |

---

## 10. Tenant Admin (Quản trị hệ thống SaaS)
* **Base URL:** `/api/v1/admin/tenants`
* Các API này dành riêng cho **SUPER_ADMIN** để quản lý các cửa hàng đăng ký trên hệ thống.

| HTTP Method | Endpoint | Quyền (Role) | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/admin/tenants` | **SUPER_ADMIN** | Lấy danh sách tất cả các cửa hàng (tenants) đang hoạt động trên hệ thống. |
| `GET` | `/api/v1/admin/tenants/{id}` | **SUPER_ADMIN** | Xem chi tiết thông tin của một cửa hàng (tenant). |
| `PATCH` | `/api/v1/admin/tenants/{id}/suspend` | **SUPER_ADMIN** | Đình chỉ (khóa tạm thời) hoạt động của một cửa hàng. |
| `PATCH` | `/api/v1/admin/tenants/{id}/activate` | **SUPER_ADMIN** | Kích hoạt lại hoạt động cho một cửa hàng đã bị khóa. |
| `GET` | `/api/v1/admin/tenants/{id}/preview` | **SUPER_ADMIN** | Xem trước cấu hình và thông tin sơ lược của cửa hàng. |

---

## 💡 Hướng dẫn cấu hình trên Postman
1. **Import Collection**: Import file [store_clothes_postman_collection.json](file:///C:/Users/ASUS/.gemini/antigravity-ide/brain/b144f02e-ebda-4a35-8bc6-973818bc07a7/store_clothes_postman_collection.json) vào Postman.
2. **Cấu hình Biến**:
   * Click vào Collection **Store Clothes POS API** -> Chọn tab **Variables**.
   * Thiết lập `base_url` (Ví dụ: `http://localhost:8080`).
3. **Đăng nhập và Lấy Token**:
   * Mở request **Authentication (Xác thực)** -> **Đăng nhập (Login)**.
   * Nhấn **Send** -> copy giá trị `accessToken` trong JSON phản hồi.
   * Quay lại tab **Variables** của Collection, dán token vào trường **Current Value** của biến `token`.
   * Nhấn **Save** (Ctrl + S). Lúc này tất cả các API yêu cầu xác thực sẽ tự động đính kèm Token trong Header nhờ cấu hình Authentication Type là `Bearer Token` ở mức Collection.
