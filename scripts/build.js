import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { getCurrentVersion, getFullVersion } from './utils/version-utils.js';
import { buildJar, copyToServer } from './utils/build-utils.js';

// Get __dirname equivalent in ES modules
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.join(__dirname, '..');

// Configuration
const MOD_NAME = 'travelers-chest';
const SERVER_MODS_DIR = 'D:\\_Outfits\\GBTI\\MinecraftServer\\.server\\mods';
if (!fs.existsSync(SERVER_MODS_DIR)) {
    fs.mkdirSync(SERVER_MODS_DIR, { recursive: true });
}
const BUILD_DIR = path.join(PROJECT_ROOT, 'build', 'libs');

console.log('🚀 Starting build process...');

// Check if server is running
console.log('⚠️  Make sure the Minecraft server is stopped before continuing.');
console.log('   Press Ctrl+C to cancel, or wait 5 seconds to continue...');
await new Promise(resolve => setTimeout(resolve, 5000));

try {
    // Get current version with build number
    const version = getFullVersion();
    console.log(`📈 Building version ${version}`);
    
    // Build jar
    const jarPath = await buildJar(version, true);
    console.log(`📦 Built jar: ${jarPath}`);
    
    // Copy to server
    await copyToServer(jarPath);
    
    console.log('✨ Build completed successfully!');
    
} catch (error) {
    console.error('❌ Build failed:', error.message);
    process.exit(1);
}
