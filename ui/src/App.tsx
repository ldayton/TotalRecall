import React from 'react';
import { Allotment } from 'allotment';
import 'allotment/dist/style.css';

function App() {
  return (
    <div style={{ height: '100vh', width: '100vw' }}>
      <Allotment vertical>
        <Allotment.Pane minSize={100}>
          <div style={{ padding: '20px', height: '100%', boxSizing: 'border-box' }}>
            <h2>Top Pane</h2>
            <p>This is the top section of your TotalRecall app</p>
          </div>
        </Allotment.Pane>
        <Allotment.Pane minSize={100}>
          <div style={{ padding: '20px', height: '100%', boxSizing: 'border-box' }}>
            <h2>Bottom Pane</h2>
            <p>This is the bottom section of your TotalRecall app</p>
          </div>
        </Allotment.Pane>
      </Allotment>
    </div>
  );
}

export default App;