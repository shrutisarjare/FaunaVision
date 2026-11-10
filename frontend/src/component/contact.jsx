import { useState } from "react";
import { apiUrl } from "../api";

const Contact = () => {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");

    try {
      const res = await fetch(apiUrl("/api/contact"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name,
          email,
          message,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.message || "Failed to send message");
      }

      // ✅ Show thank-you message in SAME section
      setSubmitted(true);

      // clear form values
      setName("");
      setEmail("");
      setMessage("");

    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <section className="py-16 text-center">
      <h2 className="text-3xl font-bold text-green-700 mb-6">
        Contact Us
      </h2>

      {!submitted ? (
        <form
          onSubmit={handleSubmit}
          className="flex flex-col gap-4 max-w-md mx-auto"
        >
          {error && (
            <p className="text-red-600 font-medium">{error}</p>
          )}

          <input
            type="text"
            placeholder="Your Name"
            className="border p-2 rounded"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />

          <input
            type="email"
            placeholder="Your Email"
            className="border p-2 rounded"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />

          <textarea
            placeholder="Your Message"
            className="border p-2 rounded"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
          />

          <button
            type="submit"
            className="bg-green-700 text-white py-2 rounded"
          >
            Send Message
          </button>
        </form>
      ) : (
        // ✅ THANK YOU MESSAGE (SAME SECTION)
        <div className="max-w-md mx-auto bg-green-50 border border-green-300 rounded-lg p-6">
          <h3 className="text-xl font-semibold text-green-700 mb-2">
            Thank you for contacting us 🌱
          </h3>
          <p className="text-gray-600">
            We’ve received your message and will get back to you soon.
          </p>
        </div>
      )}
    </section>
  );
};

export default Contact;
