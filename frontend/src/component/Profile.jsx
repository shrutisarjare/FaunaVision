import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { User } from "lucide-react";
import { currentUser, refreshSession } from "../api";

const Profile = () => {
  const navigate = useNavigate();
  // Seeded from storage so the page paints immediately, then corrected by the API.
  const [user, setUser] = useState(currentUser);

  useEffect(() => {
    let active = true;
    refreshSession().then((fresh) => {
      if (!active) return;
      if (fresh) {
        setUser(fresh);
        return;
      }
      // refreshSession only returns null after dropping a rejected token.
      navigate("/login", { replace: true });
    });
    return () => {
      active = false;
    };
  }, [navigate]);

  return (
    <div className="relative min-h-screen text-white">

      {/* Background */}
      <img
        src="https://images.unsplash.com/photo-1500530855697-b586d89ba3ee"
        className="absolute w-full h-full object-cover -z-10"
        alt=""
      />

      {/* Overlay */}
      <div className="absolute inset-0 bg-black/60 -z-10"></div>

      {/* Content */}
      <div className="flex justify-center items-center h-screen px-4">

        <div className="backdrop-blur-2xl bg-white/10 p-10 rounded-3xl border border-white/20 shadow-2xl w-full max-w-md text-center">

          {/* Avatar */}
          <div className="w-20 h-20 mx-auto mb-4 rounded-full bg-green-100 flex items-center justify-center text-green-700 shadow-md">
            <User size={36} />
          </div>

          {/* Title */}
          <h1 className="text-3xl font-bold mb-6 tracking-wide">
            Profile
          </h1>

          {/* Info */}
          <div className="text-left space-y-3 mb-6">
            <p className="text-lg">
              <span className="text-gray-300">Name:</span>{" "}
              <span className="font-semibold">{user?.name || "Not set"}</span>
            </p>

            <p className="text-lg">
              <span className="text-gray-300">Email:</span>{" "}
              <span className="font-semibold">{user?.email}</span>
            </p>

            <p className="text-lg">
              <span className="text-gray-300">Account:</span>{" "}
              <span className="font-semibold capitalize">{user?.role || "user"}</span>
            </p>
          </div>

          {/* Button */}
          <button
            onClick={() => navigate("/edit-profile")}
            className="w-full bg-gradient-to-r from-green-500 to-green-600 
                       py-3 rounded-xl font-semibold 
                       hover:scale-105 hover:shadow-lg 
                       transition-all duration-300"
          >
            Edit Profile
          </button>

        </div>

      </div>
    </div>
  );
};

export default Profile;