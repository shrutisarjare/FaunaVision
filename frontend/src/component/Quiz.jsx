import React, { useEffect, useState } from "react";
import { apiUrl } from "../api";

const Quiz = () => {
  const [questions, setQuestions] = useState([]);
  const [current, setCurrent] = useState(0);
  const [score, setScore] = useState(0);

  useEffect(() => {
    fetch(apiUrl("/api/quiz"))
      .then(res => res.json())
      .then(setQuestions);
  }, []);

  const handleAnswer = (option) => {
    if (option === questions[current].answer) {
      setScore(score + 1);
    }
    setCurrent(current + 1);
  };

  // 🎉 RESULT SCREEN
  if (current >= questions.length) {
    return (
      <div
        className="min-h-screen flex items-center justify-center bg-cover bg-center relative"
        style={{
          backgroundImage:
            "url('https://images.unsplash.com/photo-1501785888041-af3ef285b470')",
        }}
      >
        <div className="absolute inset-0 bg-black/70"></div>

        <div className="relative bg-white/10 backdrop-blur-xl border border-white/10 p-10 rounded-3xl text-center shadow-2xl text-white">
          <h1 className="text-4xl font-bold text-green-400 mb-4">
            🎉 Quiz Completed!
          </h1>

          <p className="text-2xl">
            Score: <span className="text-green-300">{score}</span> /{" "}
            {questions.length}
          </p>

          <button
            onClick={() => window.location.reload()}
            className="mt-6 bg-green-500 hover:bg-green-600 px-6 py-3 rounded-xl font-semibold transition"
          >
            Restart Quiz
          </button>
        </div>
      </div>
    );
  }

  const q = questions[current];

  return (
    <div
      className="min-h-screen flex items-center justify-center bg-cover bg-center relative px-4"
      style={{
        backgroundImage:
          "url('https://images.unsplash.com/photo-1493244040629-496f6d136cc3')",
      }}
    >
      {/* Overlay */}
      <div className="absolute inset-0 bg-black/70"></div>

      <div className="relative bg-white/10 backdrop-blur-xl border border-white/10 p-8 rounded-3xl shadow-2xl w-full max-w-xl text-white">

        {/* 📊 Progress Bar */}
        <div className="w-full bg-white/20 rounded-full h-2 mb-6">
          <div
            className="bg-green-400 h-2 rounded-full transition-all duration-300"
            style={{
              width: `${((current + 1) / questions.length) * 100}%`,
            }}
          ></div>
        </div>

        {/* Question */}
        <h2 className="text-2xl font-semibold mb-6 text-center">
          {q?.question}
        </h2>

        {/* Options */}
        <div className="flex flex-col gap-4">
          {q?.options.map((opt, i) => (
            <button
              key={i}
              onClick={() => handleAnswer(opt)}
              className="
                bg-white/20 
                hover:bg-green-500 
                hover:text-white
                text-white
                py-3 
                rounded-xl 
                transition-all 
                duration-300
                border border-white/10
              "
            >
              {opt}
            </button>
          ))}
        </div>

        {/* Footer */}
        <div className="mt-6 text-center text-gray-300">
          Question {current + 1} / {questions.length}
        </div>
      </div>
    </div>
  );
};

export default Quiz;