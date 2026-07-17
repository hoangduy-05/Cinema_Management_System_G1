export default function CgvFooter() {
  return (
    <footer className="cgv-footer">
      <div className="brands">4DX · IMAX · STARIUM · GOLD CLASS · L'AMOUR · SWEETBOX · PREMIUM CINEMA · SCREENX</div>
      <div style={{ display: "flex", gap: 60, flexWrap: "wrap", maxWidth: 1080, margin: "0 auto" }}>
        <div>
          <b>CGV Việt Nam</b>
          <p>Giới Thiệu<br />Tiện Ích Online<br />Thẻ Quà Tặng<br />Tuyển Dụng</p>
        </div>
        <div>
          <b>Điều khoản sử dụng</b>
          <p>Điều Khoản Chung<br />Chính Sách Thanh Toán<br />Chính Sách Bảo Mật</p>
        </div>
        <div>
          <b>Chăm sóc khách hàng</b>
          <p>Hotline: 1900 6017<br />Giờ làm việc: 8:00 - 22:00<br />Email: hoidap@fcinema.vn</p>
        </div>
      </div>
    </footer>
  );
}
