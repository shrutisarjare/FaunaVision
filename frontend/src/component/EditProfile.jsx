import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { User } from "lucide-react";
import { apiUrl, authHeaders, clearSession, currentUser, errorMessage, saveSession } from "../api";

const EditProfile = () => {
  const user = currentUser();
  const navigate = useNavigate();

  const [name, setName] = useState(user?.name || "");
  const [email, setEmail] = useState(user?.email || "");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSave = async () => {
    const trimmedName = name.trim();
    const trimmedEmail = email.trim();

    setError("");

    if (!trimmedName || !trimmedEmail) {
      setError("Name and email are required");
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(trimmedEmail)) {
      setError("Invalid email format");
      return;
    }

    if (!user) {
      navigate("/login", { replace: true });
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(apiUrl("/update-profile"), {
        method: "PUT",
        headers: authHeaders({ "Content-Type": "application/json" }),
        body: JSON.stringify({
          _id: user._id || user.id,
          name: trimmedName,
          email: trimmedEmail,
        }),
      });

      if (response.status === 401) {
        clearSession();
        navigate("/login", { replace: true });
        return;
      }

      if (!response.ok) {
        setError(await errorMessage(response, "Update failed"));
        return;
      }

      // Store what the API accepted, so the navbar and profile page agree with it.
      saveSession({
        user: { ...user, name: trimmedName, email: trimmedEmail.toLowerCase() },
      });
      navigate("/profile");
    } catch {
      setError("Cannot reach the server. Is the backend running?");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center bg-cover bg-center relative px-4"
      style={{
        backgroundImage:
          "url('https://images.unsplash.com/photo-1500530855697-b586d89ba3ee')",
      }}
    >
      {/* Overlay */}
      <div className="absolute inset-0 bg-black/60"></div>

      {/* Card */}
      <div className="relative backdrop-blur-2xl bg-white/10 border border-white/20 shadow-2xl p-8 rounded-3xl w-full max-w-md text-center text-white">

        {/* Avatar */}
        <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-green-100 flex items-center justify-center text-green-700">
          <User size={28} />
        </div>

        {/* Title */}
        <h1 className="text-2xl font-bold mb-6">Edit Profile</h1>

        {error && (
          <p className="text-red-300 font-medium mb-4">
            {error}
          </p>
        )}

        {/* Name */}
        <input
          type="text"
          placeholder="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="w-full mb-4 px-4 py-2 rounded-xl bg-white/80 text-black outline-none"
        />

        {/* Email */}
        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full mb-6 px-4 py-2 rounded-xl bg-white/80 text-black outline-none"
        />

        {/* Save Button */}
        <button
          onClick={handleSave}
          disabled={loading}
          className="w-full bg-gradient-to-r from-green-500 to-green-600 py-3 rounded-xl font-semibold 
                     hover:scale-105 hover:shadow-lg transition-all duration-300 disabled:opacity-50"
        >
          {loading ? "Saving..." : "Save Changes"}
        </button>

      </div>
    </div>
  );
};

export default EditProfile;