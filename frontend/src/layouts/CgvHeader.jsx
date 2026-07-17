import { Link, useNavigate } from "react-router-dom";
import { useSelector, useDispatch } from "react-redux";
import { logout } from "@/store/slices/authSlice";
import { PATHS } from "@/routes/routePaths";

export default function CgvHeader() {
  const { user } = useSelector((s) => s.auth);
  const dispatch = useDispatch();
  const navigate = useNavigate();

  return (
    <>
      <div className="cgv-topbar">
        <div className="cgv-topbar-content">
          <span>🎟 TIN MỚI &amp; ƯU ĐÃI</span>
          <Link to={PATHS.MY_TICKETS}>🎫 VÉ CỦA TÔI</Link>
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
      </div>

      <div className="cgv-stripe" />
      <div className="cgv-nav">
        <div className="cgv-nav-content">
          <Link to="/" className="logo">F-Cinema</Link>
          <div className="menu">
            <Link to="/">PHIM</Link>
            <a>RẠP F-Cinema</a>
          </div>
          <Link to="/" className="buy">MUA VÉ NGAY</Link>
        </div>
      </div>
      <div className="cgv-stripe" />
    </>
  );
}
