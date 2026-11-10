import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { apiUrl } from "../api";

const Favourites = () => {
  const [favs, setFavs] = useState([]);
  const navigate = useNavigate();

  useEffect(() => {
    fetch(apiUrl("/api/favourites"))
      .then(res => res.json())
      .then(setFavs);
  }, []);

  const handleClick = (animal) => {
    navigate(`/species/${animal}`, {
      state: { name: animal }
    });
  };

  return (
    <div
      className="min-h-screen p-10 bg-cover bg-center relative"
      style={{
        backgroundImage:
          "url('https://images.unsplash.com/photo-1470770841072-f978cf4d019e')"
      }}
    >
      {/* 🌅 SOFT DARK + WARM OVERLAY */}
      <div className="absolute inset-0 bg-black/50"></div>

      <div className="relative">
        <h1 className="text-4xl font-bold text-pink-300 mb-10">
          💖 My Favourites
        </h1>

        {favs.length === 0 ? (
          <div className="flex items-center justify-center h-[60vh]">
            <p className="text-gray-300 text-lg">No favourites yet</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {favs.map((item, index) => (
              <div
                key={index}
                onClick={() => handleClick(item.name)}
                className="
                  cursor-pointer
                  bg-white/10
                  backdrop-blur-xl
                  border border-white/10
                  p-6
                  rounded-3xl
                  shadow-lg
                  hover:scale-105
                  transition
                "
              >
                <h2 className="text-2xl font-bold text-pink-300 capitalize">
                  {item.name}
                </h2>

                <p className="text-sm text-gray-300 mt-2">
                  Added: {new Date(item.added_at).toLocaleString()}
                </p>

                <div className="mt-4 text-sm text-purple-300">
                  View full details →
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default Favourites;