import { Link, Navigate, useNavigate } from "react-router-dom";
import { Home } from "lucide-react";
import { useState } from "react";
import { apiUrl, currentUser, errorMessage } from "../api";

export default function Register() {
  const navigate = useNavigate();

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("user");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // An existing session would be replaced by the new account, so hide the form instead.
  const signedIn = currentUser();
  if (signedIn) {
    return <Navigate to={signedIn.role === "admin" ? "/AdminDashboard" : "/"} replace />;
  }

  const handleRegister = async () => {
    setError("");

    const trimmedName = name.trim();
    const trimmedEmail = email.trim();

    if (!trimmedName || !trimmedEmail || !password) {
      setError("All fields are required");
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(trimmedEmail)) {
      setError("Invalid email format");
      return;
    }

    // Kept in step with the API, which rejects anything shorter.
    if (password.length < 6) {
      setError("Password must be at least 6 characters");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(apiUrl("/api/auth/register"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        // The password is sent verbatim: trimming it here would silently change it.
        body: JSON.stringify({
          name: trimmedName,
          email: trimmedEmail,
          password,
          role,
        }),
      });

      if (!response.ok) {
        setError(await errorMessage(response, "Registration failed"));
        return;
      }

      // Registering only creates the account; the user signs in on the next screen.
      navigate("/login", { state: { registered: true, email: trimmedEmail, role } });
    } catch {
      setError("Cannot reach the server. Is the backend running?");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[url('/hero.png')] bg-cover bg-center flex items-center justify-center px-4">
      <div className="relative bg-white/90 backdrop-blur-md p-6 sm:p-8 md:p-10 rounded-2xl shadow-xl w-full max-w-sm">

        {/* Home Icon */}
        <Link to="/" className="absolute top-4 right-4 text-green-700">
          <Home />
        </Link>

        <h1 className="text-3xl font-bold text-green-700 text-center mb-6">
          Create Account 🌱
        </h1>

        {error && (
          <p className="text-red-600 text-center mb-3 font-medium">
            {error}
          </p>
        )}

        <input
          type="text"
          placeholder="Username"
          className="w-full p-3 mb-4 border rounded-lg"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />

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
          className="w-full p-3 mb-4 border rounded-lg"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
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
          onClick={handleRegister}
          disabled={loading}
          className="w-full bg-green-600 hover:bg-green-700 text-white py-3 rounded-lg disabled:opacity-60"
        >
          {loading ? "Creating account..." : "Register"}
        </button>

        <p className="text-center mt-4 text-gray-600">
          Already have an account?{" "}
          <Link to="/login" className="text-green-700 font-semibold">
            Login
          </Link>
        </p>

      </div>
    </div>
  );
}