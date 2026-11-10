import React, { useState, useEffect } from "react";
import axios from "axios";
import { API_URL, clearSession, currentUser } from "../api";

const BASE_URL = API_URL;

const AdminDashboard = () => {
  const [activeTab, setActiveTab] = useState("view");
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);

  const [region, setRegion] = useState("india");
  const [category, setCategory] = useState("terrestrial");

  const [search, setSearch] = useState("");
  const [editingRow, setEditingRow] = useState(null);

  const [newRow, setNewRow] = useState({
    animal_name: "",
    animal_type: "",
    scientific_name: "",
    state: "",
    habitat_location: "",
    population_2014: "",
    population_2016: "",
    population_2018: "",
    population_2020: "",
    population_2022: "",
    population_2024: "",
    status: "",
  });

  const user = currentUser();

  const handleLogout = () => {
    clearSession();
    window.location.href = "/login";
  };

  const fetchSheetData = async () => {
    setLoading(true);
    try {
      const res = await axios.get(
        `${BASE_URL}/admin/sheet/${region}/${category}`
      );
      setData(res.data || []);
    } catch {
      alert("Backend error ❌");
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchSheetData();
  }, [region, category]);

  const handleDelete = async (name) => {
    if (!window.confirm(`Delete ${name}?`)) return;

    try {
      await axios.delete(
        `${BASE_URL}/admin/delete/${region}/${category}/${name}`
      );
      fetchSheetData();
    } catch {
      alert("Delete failed ❌");
    }
  };

  const handleSave = async (item) => {
    try {
      await axios.put(
        `${BASE_URL}/admin/update/${region}/${category}/${item.animal_name}`,
        item
      );
      setEditingRow(null);
      fetchSheetData();
    } catch {
      alert("Update failed ❌");
    }
  };

  const handleAdd = async () => {
    try {
      await axios.post(
        `${BASE_URL}/admin/add/${region}/${category}`,
        newRow
      );

      setData((prev) => [...prev, newRow]);

      setNewRow({
        animal_name: "",
        animal_type: "",
        scientific_name: "",
        state: "",
        habitat_location: "",
        population_2014: "",
        population_2016: "",
        population_2018: "",
        population_2020: "",
        population_2022: "",
        population_2024: "",
        status: "",
      });

    } catch {
      alert("Add failed ❌");
    }
  };

  const filteredData = data.filter((row) =>
    Object.values(row).join(" ").toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="flex min-h-screen bg-gradient-to-br from-green-100 via-white to-green-200">

      {/* SIDEBAR */}
      <div className="w-64 bg-green-900 text-white p-6 flex flex-col shadow-xl">
        <h1 className="text-xl font-bold mb-6">🌿 Admin Panel</h1>

        <button onClick={() => setActiveTab("view")} className="mb-2 hover:bg-green-700 p-2 rounded">
          View Data
        </button>

        <button onClick={() => setActiveTab("update")} className="mb-2 hover:bg-green-700 p-2 rounded">
          Update Data
        </button>

        <button
          onClick={handleLogout}
          className="bg-red-500 py-2 rounded hover:bg-red-600"
        >
          Logout
        </button>
      </div>

      {/* MAIN */}
      <div className="flex-1 p-6">

        {/* NAVBAR */}
        <div className="bg-white/80 backdrop-blur-md p-4 rounded-xl shadow mb-4 flex justify-between">
          <h2 className="font-bold text-lg">Admin Dashboard</h2>
          <div>👤 {user?.email}</div>
        </div>

        {/* VIEW */}
        {activeTab === "view" && (
          <div>
            <div className="flex gap-3 mb-4">
              <select value={region} onChange={(e) => setRegion(e.target.value)}>
                <option value="india">India</option>
                <option value="panasia">Pan Asia</option>
                <option value="global">Global</option>
              </select>

              <select value={category} onChange={(e) => setCategory(e.target.value)}>
                <option value="terrestrial">Terrestrial</option>
                <option value="aquatic">Aquatic</option>
                <option value="aerial">Aerial</option>
              </select>

              <input
                type="text"
                placeholder="Search..."
                className="border px-2 rounded"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>

            <table className="w-full bg-white rounded-xl shadow">
              <thead>
                <tr>
                  {filteredData[0] &&
                    Object.keys(filteredData[0]).map((col) => (
                      <th key={col} className="bg-green-600 text-white p-2">{col}</th>
                    ))}
                  <th className="bg-green-600 text-white p-2">Actions</th>
                </tr>
              </thead>

              <tbody>
                {loading ? (
                  <tr><td colSpan="100%">Loading...</td></tr>
                ) : filteredData.length === 0 ? (
                  <tr><td colSpan="100%">No data</td></tr>
                ) : (
                  filteredData.map((item, i) => (
                    <tr key={i} className="hover:bg-green-50">
                      {Object.keys(item).map((key) => (
                        <td key={key} className="border p-2">
                          {editingRow === i ? (
                            <input
                              value={item[key]}
                              onChange={(e) => {
                                const newData = [...filteredData];
                                newData[i][key] = e.target.value;
                                setData(newData);
                              }}
                            />
                          ) : (
                            item[key]
                          )}
                        </td>
                      ))}

                      <td className="border p-2">
                        {editingRow === i ? (
                          <>
                            <button onClick={() => handleSave(item)}>💾</button>
                            <button onClick={() => setEditingRow(null)}>❌</button>
                          </>
                        ) : (
                          <>
                            <button onClick={() => setEditingRow(i)}>✏️</button>
                            <button onClick={() => handleDelete(item.animal_name)}>🗑</button>
                          </>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* UPDATE */}
        {activeTab === "update" && (
          <div className="space-y-6">

            <div className="flex gap-3">
              <select value={region} onChange={(e) => setRegion(e.target.value)}>
                <option value="india">India</option>
                <option value="panasia">Pan Asia</option>
                <option value="global">Global</option>
              </select>

              <select value={category} onChange={(e) => setCategory(e.target.value)}>
                <option value="terrestrial">Terrestrial</option>
                <option value="aquatic">Aquatic</option>
                <option value="aerial">Aerial</option>
              </select>
            </div>

            <div className="bg-white/90 backdrop-blur-md p-6 rounded-2xl shadow">
              <h3 className="font-bold mb-2">➕ Add New Row</h3>

              <div className="grid grid-cols-3 gap-2">
                {Object.keys(newRow).map((key) => (
                  <input
                    key={key}
                    placeholder={key}
                    className="border rounded p-2"
                    value={newRow[key]}
                    onChange={(e) =>
                      setNewRow({ ...newRow, [key]: e.target.value })
                    }
                  />
                ))}
              </div>

              <button
                onClick={handleAdd}
                className="bg-green-600 text-white px-4 py-2 rounded mt-3 hover:bg-green-700"
              >
                Add Row
              </button>
            </div>

            <div className="bg-white/90 backdrop-blur-md p-6 rounded-2xl shadow">
              <h3 className="font-bold mb-2">🗑 Delete Rows</h3>

              {data.map((item, i) => (
                <div key={i} className="flex justify-between border-b p-2">
                  <span>{item.animal_name}</span>
                  <button
                    className="text-red-500 hover:text-red-700"
                    onClick={() => handleDelete(item.animal_name)}
                  >
                    Delete
                  </button>
                </div>
              ))}
            </div>

          </div>
        )}

      </div>
    </div>
  );
};

export default AdminDashboard;