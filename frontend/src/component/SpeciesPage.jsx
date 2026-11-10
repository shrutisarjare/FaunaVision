import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid
} from "recharts";

import { useParams, useLocation } from "react-router-dom";
import { useEffect, useState } from "react";
import { apiUrl } from "../api";

const SpeciesPage = () => {

  // ✅ params
  const { region, category, state, animal } = useParams();
  const location = useLocation();

  const detectionData = location.state;

  const [data, setData] = useState(null);
  const [chartData, setChartData] = useState([]);

  const [regionType, setRegionType] = useState("india");
  const [selectedLocation, setSelectedLocation] = useState("");

  // 🔥 NEW: dynamic locations
  const [locations, setLocations] = useState([]);

  // =============================
  // 📊 BUILD CHART
  // =============================
  const buildChart = (result) => {
    

    const years = [2014, 2016, 2018, 2020, 2022, 2024];

    let history = [];

    if (
      Array.isArray(result.population_history) &&
      typeof result.population_history[0] === "number"
    ) {
      history = result.population_history.map((val, i) => ({
        year: years[i],
        population: Number(val) || 0,
      }));
    } else {
      history = (result.population_history || []).map(item => ({
        year: item.year,
        population: Number(item.population) || 0,
      }));
    }

    const predictions = (result.predictions || []).map(p => ({
      year: p.year,
      population: Number(p.population) || 0,
    }));

    setChartData([...history, ...predictions]);
  };

  // =============================
  // 🔁 MAIN LOAD
  // =============================
  useEffect(() => {

    // 🟢 DETECTION FLOW
    if (detectionData) {

      const animalName = detectionData.name || detectionData.species;
      
      fetch(apiUrl(`/api/history?animal=${animalName}`), {
  method: "POST"
});

      // main data
      fetch(apiUrl(`/detection/${animalName}`))
        .then(res => res.json())
        .then(result => {

          if (result.error) {
            setData({ error: result.error });
            return;
          }

          setData(result);
          buildChart(result);
        });

      // 🔥 NEW: fetch locations
      fetch(apiUrl(`/detection-locations/${animalName}/${regionType}`))
        .then(res => res.json())
        .then(result => {
          setLocations(result || []);
        });

      return;
    }


    // 🔵 EXPLORE FLOW
    if (region && category && state && animal) {
      // ✅ SAVE HISTORY (explore flow)
fetch(apiUrl(`/api/history?animal=${animal}&source=explore`), {
  method: "POST"
});

      fetch(apiUrl(`/analysis/${region}/${category}/${state}/${animal}`))
        .then(res => res.json())
        .then(result => {

          if (result.error) {
            setData({ error: result.error });
            return;
          }

          setData(result);
          buildChart(result);
        });
    }

  }, [region, category, state, animal, detectionData, regionType]);

  // =============================
  // 🟢 DROPDOWN CHANGE
  // =============================
  const handleDropdownChange = (value) => {

    setSelectedLocation(value);

    const animalName = detectionData.name || detectionData.species;

    fetch(apiUrl(`/detection/${animalName}/${regionType}/${value}`))
      .then(res => res.json())
      .then(result => {

        if (result.error) {
          setData({ error: result.error });
          return;
        }

        setData(result);
        buildChart(result);
      });
  };

  // =============================
  // 🔄 REGION CHANGE
  // =============================
  const handleRegionChange = (value) => {

    setRegionType(value);
    setSelectedLocation("");

    const animalName = detectionData.name || detectionData.species;

    // 🔥 fetch new locations
    fetch(apiUrl(`/detection-locations/${animalName}/${value}`))
      .then(res => res.json())
      .then(result => {
        setLocations(result || []);
      });

    // reload base data
    fetch(apiUrl(`/detection/${animalName}`))
      .then(res => res.json())
      .then(result => {

        if (result.error) {
          setData({ error: result.error });
          return;
        }

        setData(result);
        buildChart(result);
      });
  };

  // =============================
  // ⏳ LOADING / ERROR
  // =============================
  if (!data) {
    return (
      <div className="flex justify-center items-center h-screen text-xl">
        Loading...
      </div>
    );
  }

  if (data.error) {
    return (
      <div className="flex justify-center items-center h-screen text-red-500 text-xl">
        Error: {data.error}
      </div>
    );
  }

  const currentPopulation =
    Array.isArray(data.population_history) &&
    typeof data.population_history[0] === "number"
      ? data.population_history[data.population_history.length - 1]
      : data.population_history?.[data.population_history.length - 1]?.population || "N/A";

  // =============================
  // 🎨 UI
  // =============================
  return (
    <div className="min-h-screen bg-gray-100 p-10">

      <div className="max-w-5xl mx-auto bg-white p-8 rounded-xl shadow">

        {/* HEADER */}
        <div className="flex gap-10 items-center">

          <img
            src={data.image}
            alt={data.species}
            className="w-60 h-44 object-cover rounded-lg"
          />

          <div>
            <h1 className="text-3xl font-bold text-green-700">
              {data.species}
            </h1>
           <button
  onClick={() => {
    const animalName = data?.species; // ✅ correct field

    fetch(apiUrl(`/api/favourites?animal=${animalName}`), {
      method: "POST"
    })
      .then(res => res.json())
      .then(() => {
        alert("Added to favourites ❤️");
      })
      .catch(() => {
        alert("Error adding favourite");
      });
  }}
  className="mt-4 bg-yellow-500 px-4 py-2 rounded"
>
  ❤️ Add to Favourites
</button>

            <p className="italic text-gray-500">
              {data.scientific_name}
            </p>

            <p className="mt-2">Location: {data.state}</p>
            <p>Population: {currentPopulation}</p>
            <p>Status: {data.risk_level}</p>
          </div>

        </div>

        {/* 🟢 DROPDOWN */}
        {detectionData && (
          <div className="mt-6 flex gap-4">

            <select
              value={regionType}
              onChange={(e) => handleRegionChange(e.target.value)}
              className="border p-2 rounded"
            >
              <option value="india">India</option>
              <option value="panasia">Pan Asia</option>
              <option value="global">Global</option>
            </select>

            <select
              value={selectedLocation}
              onChange={(e) => handleDropdownChange(e.target.value)}
              className="border p-2 rounded"
            >
              <option value="">Select Location</option>

              {/* 🔥 dynamic locations */}
              {locations.map((loc) => (
                <option key={loc} value={loc}>
                  {loc}
                </option>
              ))}

            </select>

          </div>
        )}

        {/* CARDS */}
        <div className="grid grid-cols-3 gap-6 mt-10">

          <div className="bg-green-100 p-4 rounded">
            <h3>Trend</h3>
            <p className="text-xl">{data.trend || "N/A"}</p>
          </div>

          <div className="bg-blue-100 p-4 rounded">
            <h3>Predictions</h3>

            {(data.predictions || []).length > 0 ? (
              data.predictions.map(p => (
                <p key={p.year}>
                  {p.year}: {Math.round(p.population)}
                </p>
              ))
            ) : (
              <p>No prediction data</p>
            )}

          </div>

          <div className="bg-red-100 p-4 rounded">
            <h3>Risk</h3>
            <p className="text-xl">{data.risk_level}</p>
          </div>

        </div>

        {/* CHART */}
        {chartData.length > 0 && (
          <div className="mt-10">

            <h2 className="text-xl font-semibold mb-4">
              Population Trend (2014–2024 + Prediction)
            </h2>

            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3"/>
                <XAxis dataKey="year"/>
                <YAxis/>
                <Tooltip/>

                <Line
                  type="monotone"
                  dataKey="population"
                  stroke="#16a34a"
                  strokeWidth={3}
                />
              </LineChart>
            </ResponsiveContainer>

          </div>
        )}

      </div>

    </div>
  );
};

export default SpeciesPage;