import { Routes, Route } from 'react-router-dom';

function App() {
  return (
    <Routes>
      <Route path="/" element={<div>Home</div>} />
      <Route path="/login" element={<div>Login</div>} />
      <Route path="/register" element={<div>Register</div>} />
      <Route path="/jobs" element={<div>Jobs</div>} />
      <Route path="/jobs/:id" element={<div>Job Detail</div>} />
      <Route path="/profile" element={<div>Profile</div>} />
      <Route path="/interviews" element={<div>Interviews</div>} />
      <Route path="/assessments" element={<div>Assessments</div>} />
    </Routes>
  );
}

export default App;