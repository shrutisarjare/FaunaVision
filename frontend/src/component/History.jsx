import React, { useEffect, useState } from "react";
import { apiUrl } from "../api";

const History = () => {
  const [history, setHistory] = useState([]);

  useEffect(() => {
    fetch(apiUrl("/api/history"))
      .then(res => res.json())
      .then(data => setHistory(data))
      .catch(err => console.error("Error fetching history:", err));
  }, []);

  return (
    <div
      className="min-h-screen bg-cover bg-center relative p-10"
      style={{
        backgroundImage:
          "url('https://images.unsplash.com/photo-1501785888041-af3ef285b470')"
      }}
    >
      {/* 🔥 DARK OVERLAY */}
      <div className="absolute inset-0 bg-black/70"></div>

      <div className="relative">
        <h1 className="text-4xl font-bold text-green-300 mb-8">
          🕘 History
        </h1>

        {history.length === 0 ? (
          <div className="flex items-center justify-center h-[60vh]">
            <p className="text-gray-300 text-lg">No history yet</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {history.map((item, index) => (
              <div
                key={index}
                className="
                  bg-white/10 
                  backdrop-blur-xl 
                  border border-white/10 
                  p-6 
                  rounded-2xl 
                  shadow-lg 
                  hover:scale-105 
                  transition
                "
              >
                <h2 className="text-xl font-semibold text-green-300">
                  {item.name}
                </h2>

                <p className="text-gray-400 text-sm mt-2">
                  {new Date(item.timestamp).toLocaleString()}
                </p>

                <p className="text-sm mt-2 text-gray-300">
                  Source: {item.source}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default History;