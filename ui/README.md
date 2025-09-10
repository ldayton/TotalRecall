# TotalRecall

Electron desktop application built with React, TypeScript, and Vite.

## Prerequisites

- Node.js (v18 or higher)
- npm (v8 or higher)

## Getting Started

After cloning the repository:

```bash
# Install dependencies
npm install

# Start the development server
npm start
```

The app will launch in development mode with hot reload enabled. Any changes to the React components will automatically update in the Electron window.

## Available Scripts

- `npm start` - Runs the app in development mode
- `npm run package` - Packages the app for the current platform
- `npm run make` - Creates distributable installers
- `npm run lint` - Runs ESLint on TypeScript files
- `npm run publish` - Publishes distributables (requires configuration)

## Tech Stack

- **Electron** - Desktop application framework
- **React** - UI library
- **TypeScript** - Type-safe JavaScript
- **Vite** - Fast build tool and dev server
- **Electron Forge** - Build and packaging toolchain

## Project Structure

```
src/
├── main.ts         # Electron main process
├── preload.ts      # Preload script for security
├── renderer.tsx    # React app entry point
├── App.tsx         # Main React component
└── index.css       # Global styles
```

## Building for Production

```bash
# Create a distributable package
npm run make
```

This will create platform-specific installers in the `out/` directory.