import { useState } from 'react';
import { formatBytes } from '../utils/format.js';

export function FilePicker({ file, onFile }) {
  const [dragging, setDragging] = useState(false);

  return (
    <div
      className={`dropzone ${dragging ? 'dropzone-active' : ''}`}
      onDragOver={(event) => {
        event.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault();
        setDragging(false);
        const dropped = event.dataTransfer.files[0];
        if (dropped) {
          onFile(dropped);
        }
      }}
    >
      {file ? (
        <>
          <p className="file-name">{file.name}</p>
          <p className="muted">{formatBytes(file.size)}</p>
          <button type="button" className="link-button" onClick={() => onFile(null)}>
            Remove
          </button>
        </>
      ) : (
        <>
          <p>Drag and drop a file here, or</p>
          <label className="btn btn-secondary">
            Choose File
            <input
              type="file"
              className="visually-hidden"
              onChange={(event) => {
                onFile(event.target.files?.[0] ?? null);
                event.target.value = '';
              }}
            />
          </label>
          <p className="muted small">Up to 50 MB</p>
        </>
      )}
    </div>
  );
}
