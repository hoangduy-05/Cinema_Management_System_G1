import { Navigate, useLocation } from "react-router-dom";
import { useSelector } from "react-redux";
import { PATHS } from "@/routes/routePaths";

// Chặn route theo đăng nhập + role. allowedRoles rỗng = chỉ cần đăng nhập.
export default function ProtectedRoute({ children, allowedRoles = [] }) {
  const location = useLocation();
  const { token, user } = useSelector((s) => s.auth);

  if (!token) {
    return <Navigate to={PATHS.LOGIN} state={{ from: location }} replace />;
  }
  if (allowedRoles.length && !allowedRoles.includes(user?.role)) {
    return <Navigate to={PATHS.HOME} replace />;
  }
  return children;
}
