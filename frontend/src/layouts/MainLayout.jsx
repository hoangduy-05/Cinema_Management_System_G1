import CgvHeader from "./CgvHeader";
import CgvFooter from "./CgvFooter";

export default function MainLayout({ children }) {
  return (
    <>
      <CgvHeader />
      <main>{children}</main>
      <CgvFooter />
    </>
  );
}
