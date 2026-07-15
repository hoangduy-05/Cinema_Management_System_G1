import { useState } from "react";
import { useDispatch } from "react-redux";
import { useNavigate, useLocation } from "react-router-dom";
import { authApi } from "@/api/services/authApi";
import { setCredentials } from "@/store/slices/authSlice";
import MainLayout from "@/layouts/MainLayout";
import "./LoginPage.css";

export default function LoginPage() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from || "/";

  const [tab, setTab] = useState("login");
  const [usernameOrEmail, setU] = useState("");
  const [password, setP] = useState("");
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true); setErr(null);
    try {
      const auth = await authApi.login({ usernameOrEmail, password });
      dispatch(setCredentials({
        token: auth.accessToken,
        user: { accountId: auth.accountId, username: auth.username, email: auth.email, roles: auth.roles },
      }));
      navigate(from, { replace: true });
    } catch (e) { setErr(e.message); }
    finally { setBusy(false); }
  };

  return (
    <MainLayout>
      <div className="login-wrap">
        <div className="login-card">
          <div className="login-tabs">
            <button className={tab === "login" ? "on" : ""} onClick={() => setTab("login")}>ĐĂNG NHẬP</button>
            <button className={tab === "register" ? "on" : ""} onClick={() => setTab("register")}>ĐĂNG KÝ</button>
          </div>

          <div className="login-body">
            {err && <div className="login-err">{err}</div>}

            <label>Email hoặc số điện thoại</label>
            <input value={usernameOrEmail} onChange={(e) => setU(e.target.value)} placeholder="Email hoặc số điện thoại" />

            <label>Mật khẩu</label>
            <input type="password" value={password} onChange={(e) => setP(e.target.value)}
                   placeholder="Mật khẩu" onKeyDown={(e) => e.key === "Enter" && submit()} />

            <button className="login-submit" onClick={submit} disabled={busy}>
              {busy ? "ĐANG XỬ LÝ…" : "ĐĂNG NHẬP"}
            </button>
            <div className="login-forgot">Bạn muốn tìm lại mật khẩu?</div>
          </div>
        </div>

        <div className="login-promo">
          <div className="promo-illus">🎟️</div>
          <h3>CHƯƠNG TRÌNH KHUYẾN MÃI</h3>
          <p>Nhiều chương trình hấp dẫn<br />dành riêng cho thành viên CGV</p>
          <div className="promo-dots"><span className="on" /><span /><span /></div>
        </div>
      </div>
    </MainLayout>
  );
}
