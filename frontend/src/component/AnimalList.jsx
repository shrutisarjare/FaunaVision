import { useParams, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { apiUrl } from "../api";

const AnimalList = () => {
  
  const params = useParams();

const region = params.region;
const category = params.category;
const state = params.state;
const animal = params.animal;
  const navigate = useNavigate();

  const [animals, setAnimals] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {

    const fetchAnimals = async () => {

      try {

        // ✅ FINAL FIX: safe cleaning
        const cleanAnimal = (animal || "").toLowerCase().trim();

        if (!cleanAnimal) {
          setAnimals([]);
          setLoading(false);
          return;
        }

        let url = "";

        // 🔥 DETECTION FLOW (FIXED)
        if (!region) {
          url = apiUrl(`/search/${cleanAnimal}`);
        }

        // 🟢 NAVIGATION FLOW (UNCHANGED)
        else {

          if (!region || !category || !state) {
            setAnimals([]);
            setLoading(false);
            return;
          }

          const formattedState = encodeURIComponent(state.toLowerCase());

          url = apiUrl(`/api/animal/${region}/${category}/${formattedState}/${cleanAnimal}`);
        }

        console.log("🚀 CALLING:", url);

        const res = await fetch(url);

        if (!res.ok) {
          console.error("❌ API ERROR:", res.status);
          setAnimals([]);
          return;
        }

        const data = await res.json();

        console.log("📦 DATA:", data);

        // ✅ FINAL FIX: always ensure array
        setAnimals(Array.isArray(data) ? data : []);

      } catch (err) {
        console.error("❌ FETCH ERROR:", err);
        setAnimals([]);
      } finally {
        setLoading(false);
      }

    };

    fetchAnimals();

  }, [region, category, state, animal]);


  // 🔥 LOADING
  if (loading) {
    return (
      <div className="min-h-screen flex justify-center items-center text-xl">
        <div className="animate-pulse text-gray-600">
          Loading animals...
        </div>
      </div>
    );
  }


  return (

    <div className="min-h-screen bg-gradient-to-br from-green-50 to-gray-100 py-12 px-6">

      <h1 className="text-4xl font-bold text-center text-green-700 mb-12 capitalize">
        {region === "all"
          ? `All ${animal}`
          : `${animal} `}
      </h1>

      {/* ✅ FINAL FIX: safe condition */}
      {!animals || animals.length === 0 ? (

        <div className="flex flex-col items-center justify-center text-center mt-20">

          <img
            src="https://cdn-icons-png.flaticon.com/512/616/616408.png"
            alt="no data"
            className="w-24 mb-4 opacity-60"
          />

          <p className="text-xl text-gray-600 font-medium">
            No data found for {animal} {region === "all" ? "" : `in ${state}`}
          </p>

          <p className="text-gray-500 mt-2">
            Try exploring another state or species.
          </p>

        </div>

      ) : (

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-10 max-w-6xl mx-auto">

          {animals.map((item, index) => (

            <div
              key={item.name + index}
              onClick={() => {

  // 🟢 DETECTION FLOW (no region)
  if (!region) {
    navigate(`/species/${item.name}`, {
      state: item
    });
  }

  // 🔵 EXPLORE FLOW (full params)
  else {
    navigate(`/species-info/${region}/${category}/${state}/${animal}`);
  }

}}
              className="bg-white rounded-2xl shadow-md overflow-hidden 
                         hover:shadow-xl hover:-translate-y-1 transition-all duration-300 cursor-pointer"
            >

              <div className="w-full h-48 bg-gray-200 overflow-hidden">
                <img
                  src={item.image}
                  alt={item.name}
                  loading="lazy"
                  className="w-full h-full object-cover transition-opacity duration-500 opacity-0"
                  onLoad={(e) => e.target.style.opacity = 1}
                  onError={(e) => e.target.style.display = "none"}
                />
              </div>

              <div className="p-6">

                <h2 className="text-xl font-bold text-green-700 capitalize">
                  {item.name}
                </h2>

                <p className="text-gray-500 italic">
                  {item.scientific_name}
                </p>

                <p className="text-gray-600 mt-2">
                  📍 {item.location}
                </p>

                <p className="text-red-600 mt-2 font-semibold">
                  Status: {item.status}
                </p>

              </div>

            </div>

          ))}

        </div>

      )}

    </div>

  );

};

export default AnimalList;