import { Link, useNavigate } from "react-router-dom";
import { useState } from "react";
import {
  User,
  Pencil,
  History,
  Heart,
  Brain,
  Settings,
  LogOut,
  Menu,
  ShieldCheck
} from "lucide-react";
import { clearSession, currentUser } from "../api";

const Navbar = () => {
  const navigate = useNavigate();
  const user = currentUser();
  const [open, setOpen] = useState(false);

  const handleLogout = () => {
    clearSession();
    setOpen(false);
    navigate("/login");
  };

  return (
    <>
      {/* NAVBAR */}
      <nav className="bg-green-700 text-white px-8 py-4 flex justify-between items-center shadow-md">
        
        {/* Logo */}
        <Link to="/" className="text-xl font-bold tracking-wide">
           FaunaVision
        </Link>

        <ul className="flex gap-6 items-center">
          <li className="cursor-pointer hover:text-yellow-300 transition">Features</li>
          <li className="cursor-pointer hover:text-yellow-300 transition">How It Works</li>
          <li className="cursor-pointer hover:text-yellow-300 transition">Species</li>
          <li className="cursor-pointer hover:text-yellow-300 transition">Contact</li>

          {!user ? (
            <li>
              <Link
                to="/login"
                className="border px-4 py-1 rounded-lg hover:bg-white hover:text-green-700 transition"
              >
                Login
              </Link>
            </li>
          ) : (
            <li>
              <button
                onClick={() => setOpen(true)}
                className="bg-white/20 p-2 rounded-full hover:bg-white/30 transition"
              >
                <User size={20} />
              </button>
            </li>
          )}
        </ul>
      </nav>

      {/* DRAWER */}
      {open && (
        <>
          {/* Overlay */}
          <div
            className="fixed inset-0 bg-black/40 backdrop-blur-sm z-40"
            onClick={() => setOpen(false)}
          ></div>

          {/* Sidebar */}
          <div className="fixed top-0 right-0 h-full w-80 bg-white z-50 shadow-2xl p-6 flex flex-col rounded-l-2xl transform transition-transform duration-300">

            {/* Header */}
            <div className="flex items-center gap-3 mb-8 border-b pb-4">
              <div className="w-12 h-12 rounded-full bg-green-100 flex items-center justify-center text-green-700">
                <User size={22} />
              </div>

              <div>
                <h2 className="text-lg font-semibold text-gray-800">
                  {user.name}
                </h2>
                <p className="text-sm text-gray-500">{user.email}</p>
              </div>
            </div>

            {/* Menu */}
            <ul className="flex flex-col gap-2 text-gray-700">

              {[
                { name: "Profile", icon: User, path: "/profile" },
                // Only an administrator has anything to do in the admin panel.
                ...(user.role === "admin"
                  ? [{ name: "Admin Panel", icon: ShieldCheck, path: "/AdminDashboard" }]
                  : []),
                { name: "History", icon: History, path: "/history" },
                { name: "Favourites", icon: Heart, path: "/favourites" },
                { name: "Quiz", icon: Brain, path: "/quiz" },
                { name: "Settings", icon: Settings, path: "/settings" },
              ].map((item, i) => {
                const Icon = item.icon;
                return (
                  <li
                    key={i}
                    onClick={() => {
                      navigate(item.path);
                      setOpen(false);
                    }}
                    className="flex items-center gap-3 px-4 py-3 rounded-xl cursor-pointer 
                               hover:bg-green-50 hover:text-green-700 
                               transition-all duration-200 group"
                  >
                    <Icon
                      size={18}
                      className="text-gray-500 group-hover:text-green-700"
                    />
                    <span className="font-medium">{item.name}</span>
                  </li>
                );
              })}

            </ul>

            {/* Logout */}
            <button
              onClick={handleLogout}
              className="mt-auto flex items-center justify-center gap-2 
                         bg-red-500 hover:bg-red-600 text-white py-2 rounded-xl 
                         transition-all duration-200 shadow-md"
            >
              <LogOut size={18} />
              Logout
            </button>
          </div>
        </>
      )}
    </>
  );
};

export default Navbar;