# Cinema Management System — Frontend (SWD392, Team 1)

ReactJS + Vite. Cấu trúc thư mục bám sát **Frontend Package Diagram (SDS mục 1.2.2)** — 23 package.

## Chạy dự án
```bash
cp .env.example .env      # chỉnh VITE_API_BASE_URL trỏ tới Spring Boot
npm install
npm run dev
```

## Ánh xạ package SDS → thư mục
| # | Package (SDS) | Thư mục |
|---|---|---|
| 01 | routes | src/routes |
| 02 | layout | src/layouts |
| 03 | pages | src/pages |
| 04 | components | src/components |
| 05-12 | feature modules | src/features/* |
| 13 | api-services | src/api/services |
| 14 | api-client | src/api/client |
| 15 | DTO | src/dto |
| 16 | mapper | src/mappers |
| 17 | assets/styles | src/assets |
| 18 | security | src/security |
| 19 | store | src/store |
| 20 | config/constant | src/config |
| 21 | hooks | src/hooks (+ hook riêng trong từng feature) |
| 22 | utils | src/utils |
| 23 | notification | src/notification |

