import * as fs from 'fs';
import * as path from 'path';
import { exec } from 'child_process';
import { promisify } from 'util';
import { fileURLToPath } from 'url';
import { updateVersionInGradle } from './version-utils.js';

const execAsync = promisify(exec);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.join(__dirname, '../..');

export const BUILD_DIR = path.join(PROJECT_ROOT, 'build', 'libs');
export const MOD_NAME = 'travelers-chest';

/**
 * Cleans the build directory
 */
export async function cleanBuildDir() {
    console.log('🗑️ Clearing build directory...');
    if (fs.existsSync(BUILD_DIR)) {
        const files = fs.readdirSync(BUILD_DIR);
        for (const file of files) {
            const filePath = path.join(BUILD_DIR, file);
            fs.unlinkSync(filePath);
            console.log(`   Deleted: ${file}`);
        }
    } else {
        fs.mkdirSync(BUILD_DIR, { recursive: true });
    }
}

/**
 * Builds the jar file
 * @param {string} version - The version to build
 * @param {boolean} includeBuildNumber - Whether to include build number in version
 * @returns {string} Path to the built jar
 */
export async function buildJar(version, includeBuildNumber = false) {
    console.log('📦 Building jar...');
    
    // Update version in gradle.properties
    updateVersionInGradle(version, includeBuildNumber);
    
    // Clean build directory
    await cleanBuildDir();
    
    // Run Gradle build
    await execAsync('gradlew.bat build', { stdio: 'inherit', cwd: PROJECT_ROOT });
    
    // Find the built jar
    const files = fs.readdirSync(BUILD_DIR);
    const jarFile = files.find(file => 
        !file.includes('-sources') && 
        !file.includes('-dev') &&
        file.endsWith('.jar')
    );
    
    if (!jarFile) {
        throw new Error('Could not find built jar file');
    }
    
    // Rename jar to use specified version number
    const jarPath = path.join(BUILD_DIR, jarFile);
    const newJarName = `${MOD_NAME}-${version}.jar`;
    const newJarPath = path.join(BUILD_DIR, newJarName);
    
    if (jarPath !== newJarPath) {
        fs.renameSync(jarPath, newJarPath);
    }
    
    return newJarPath;
}

/**
 * Copies the jar to the server mods directory
 * @param {string} jarPath - Path to the jar file
 */
export async function copyToServer(jarPath) {
    const SERVER_MODS_DIR = 'D:\\_Outfits\\GBTI\\MinecraftServer\\.server\\mods';
    
    // Delete old version from server mods
    console.log('🗑️ Removing old version from server...');
    const oldFiles = fs.readdirSync(SERVER_MODS_DIR);
    for (const file of oldFiles) {
        if (file.includes(MOD_NAME)) {
            fs.unlinkSync(path.join(SERVER_MODS_DIR, file));
            console.log(`   Deleted: ${file}`);
        }
    }
    
    // Copy new version
    console.log('📋 Copying new version to server...');
    fs.copyFileSync(jarPath, path.join(SERVER_MODS_DIR, path.basename(jarPath)));
}

/**
 * Manages jar files in the project root directory
 * @param {string} jarPath - Path to the new jar file
 * @returns {string} Path to the copied jar file in project root
 */
export function manageProjectJars(jarPath) {
    console.log('🗑️ Cleaning project root jars...');
    
    // Delete existing jar files in project root
    const files = fs.readdirSync(PROJECT_ROOT);
    for (const file of files) {
        if (file.startsWith('travelers-chest') && file.endsWith('.jar')) {
            const filePath = path.join(PROJECT_ROOT, file);
            fs.unlinkSync(filePath);
            console.log(`   Deleted: ${file}`);
        }
    }
    
    // Copy new jar to project root
    const jarName = path.basename(jarPath);
    const targetPath = path.join(PROJECT_ROOT, jarName);
    fs.copyFileSync(jarPath, targetPath);
    console.log(`📋 Copied jar to project root: ${jarName}`);
    
    return targetPath;
}
