import { Link, Navigate, useNavigate, useLocation } from "react-router-dom";
import { Home } from "lucide-react";
import { useState } from "react";
import { apiUrl, currentUser, errorMessage, saveSession } from "../api";

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();

  // A freshly registered account arrives with the form already filled in.
  const [email, setEmail] = useState(location.state?.email || "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const [role, setRole] = useState(location.state?.role || "user");
  const justRegistered = Boolean(location.state?.registered);

  // Someone who is already signed in has no reason to see this form again.
  const signedIn = currentUser();
  if (signedIn) {
    return <Navigate to={signedIn.role === "admin" ? "/AdminDashboard" : "/"} replace />;
  }

  const handleLogin = async () => {
    setError("");
    setLoading(true);

    try {
      const response = await fetch(apiUrl("/api/auth/login"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        // The role is sent so the API can refuse an admin login to a normal account.
        body: JSON.stringify({ email: email.trim(), password, role }),
      });

      if (!response.ok) {
        setError(await errorMessage(response, "Login failed"));
        return;
      }

      const data = await response.json();
      saveSession(data);

      // Go back to the page the guard interrupted, otherwise home or the admin panel.
      const destination =
        data.user?.role === "admin"
          ? location.state?.from || "/AdminDashboard"
          : location.state?.from || "/";
      navigate(destination, { replace: true });
    } catch {
      setError("Cannot reach the server. Is the backend running?");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[url('/hero.png')] bg-cover bg-center flex items-center justify-center px-4">
      <div className="relative bg-white/90 backdrop-blur p-6 sm:p-8 md:p-10 rounded-2xl shadow-xl w-full max-w-sm">

        {/* Home Icon */}
        <Link to="/" className="absolute top-4 right-4 text-green-700">
          <Home />
        </Link>

        <h1 className="text-3xl font-bold text-green-700 text-center mb-2">
          Welcome Back 🌿
        </h1>

        <p className="text-center text-gray-500 mb-4">
          Login to continue
        </p>

        {justRegistered && !error && (
          <p className="text-green-800 bg-green-50 border border-green-200 rounded-lg text-center px-3 py-2 mb-3">
            Account created. Please login to continue.
          </p>
        )}

        {error && (
          <p className="text-red-600 text-center mb-3 font-medium">
            {error}
          </p>
        )}

        <input
          type="email"
          placeholder="Email"
          className="w-full p-3 mb-4 border rounded-lg"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        <input
          type="password"
          placeholder="Password"
          className="w-full p-3 mb-6 border rounded-lg"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !loading) handleLogin();
          }}
        />
        <select
          className="w-full p-3 mb-6 border rounded-lg"
          value={role}
          onChange={(e) => setRole(e.target.value)}
        >
          <option value="user">User</option>
          <option value="admin">Admin</option>
        </select>

        <button
          type="button"
          onClick={handleLogin}
          disabled={loading}
          className="w-full bg-green-600 hover:bg-green-700 text-white py-3 rounded-lg text-lg transition disabled:opacity-60"
        >
          {loading ? "Logging in..." : "Login"}
        </button>

        <p className="text-center mt-4 text-gray-600">
          Don’t have an account?{" "}
          <Link to="/register" className="text-green-700 font-semibold">
            Register
          </Link>
        </p>

      </div>
    </div>
  );
}
