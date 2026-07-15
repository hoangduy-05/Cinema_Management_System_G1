import { toast } from "react-toastify";
export const notify = {
  success: (m) => toast.success(m),
  error: (m) => toast.error(m),
  warn: (m) => toast.warn(m),
};
