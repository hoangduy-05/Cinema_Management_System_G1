import { Link, useNavigate } from "react-router-dom";
import { useSelector, useDispatch } from "react-redux";
import { logout } from "@/store/slices/authSlice";

export default function CgvHeader() {
  const { user } = useSelector((s) => s.auth);
  const dispatch = useDispatch();
  const navigate = useNavigate();

  return (
    <>
      <div className="cgv-topbar">
        <span>🎟 TIN MỚI &amp; ƯU ĐÃI</span>
        <span>🎫 VÉ CỦA TÔI</span>
        {user ? (
          <span>
            👤 XIN CHÀO, <b>{user.username}</b>!{" "}
            <a onClick={() => { dispatch(logout()); navigate("/"); }} style={{ cursor: "pointer" }}>THOÁT</a>
          </span>
        ) : (
          <Link to="/login">👤 ĐĂNG NHẬP/ ĐĂNG KÝ</Link>
        )}
        <span className="lang"><span className="on">VN</span><span>EN</span></span>
      </div>

      <div className="cgv-stripe" />
      <div className="cgv-nav">
        <Link to="/" className="logo">CGV<span>*</span></Link>
        <div className="menu">
          <Link to="/">PHIM</Link>
          <a>RẠP CGV</a>
          <a>THÀNH VIÊN</a>
          <a>CULTUREPLEX</a>
        </div>
        <Link to="/" className="buy">🎬 MUA VÉ NGAY</Link>
      </div>
      <div className="cgv-stripe" />
    </>
  );
}
