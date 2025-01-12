import * as fs from 'fs';
import * as path from 'path';
import { exec } from 'child_process';
import { promisify } from 'util';
import * as dotenv from 'dotenv';
import { fileURLToPath } from 'url';
import { getCurrentVersion, incrementVersion, updateVersion, revertVersion } from './utils/version-utils.js';
import { buildJar, manageProjectJars } from './utils/build-utils.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Load environment variables
dotenv.config({ path: path.join(__dirname, '.env') });

const execAsync = promisify(exec);

async function verifyGitHubConfig() {
    if (!process.env.GITHUB_TOKEN) {
        throw new Error('GITHUB_TOKEN not set in .env file');
    }
    if (!process.env.GITHUB_REPO) {
        throw new Error('GITHUB_REPO not set in .env file');
    }
    
    // Test GitHub API access
    console.log(`Verifying GitHub access for repository: ${process.env.GITHUB_REPO}`);
    const apiUrl = `https://api.github.com/repos/${process.env.GITHUB_REPO}`;
    console.log(`Testing API URL: ${apiUrl}`);
    
    const testCommand = `curl -s -i -H "Authorization: token ${process.env.GITHUB_TOKEN}" "${apiUrl}"`;
    
    try {
        const { stdout, stderr } = await execAsync(testCommand);
        if (stderr) {
            console.error('Test API call stderr:', stderr);
        }
        
        // Log the full response for debugging
        console.log('API Response:', stdout);
        
        // Parse the response, skipping HTTP headers
        const responseBody = stdout.split('\r\n\r\n').slice(-1)[0];
        const response = JSON.parse(responseBody);
        
        if (response.message === 'Not Found') {
            throw new Error(`Repository '${process.env.GITHUB_REPO}' not found. Please check GITHUB_REPO in .env`);
        }
        if (response.message === 'Bad credentials') {
            throw new Error('Invalid GitHub token. Please check GITHUB_TOKEN in .env');
        }
        
        console.log(`✓ GitHub repository verified: ${response.full_name}`);
        return response.full_name; // Return the actual repo name from GitHub
    } catch (error) {
        if (error.message.includes('Not Found')) {
            throw new Error(`Repository '${process.env.GITHUB_REPO}' not found. Please check GITHUB_REPO in .env`);
        }
        throw error;
    }
}

async function getChangelogContent(version) {
    try {
        const changelogPath = path.join(process.cwd(), '.product', 'changelog.md');
        const changelogContent = fs.readFileSync(changelogPath, 'utf8');
        const versionHeader = `## [${version}]`;
        const lines = changelogContent.split('\n');
        let content = [];
        let isInSection = false;
        
        for (let line of lines) {
            if (line.startsWith(versionHeader)) {
                isInSection = true;
                continue;
            } else if (isInSection && line.startsWith('## [')) {
                break;
            } else if (isInSection && line.trim()) {
                content.push(line);
            }
        }

        return content.join('\n').trim();
    } catch (error) {
        console.error('Error reading changelog:', error);
        return '';
    }
}

async function createGitHubRelease(version, jarPath, changelogContent, repoFullName) {
    try {
        const tag = `v${version}`;
        
        // Create and push tag
        await execAsync(`git tag ${tag}`);
        await execAsync('git add .');
        await execAsync(`git commit -m "Release ${tag}"`);
        await execAsync('git push origin develop --tags');

        // Create GitHub release with jar attachment
        const releaseData = {
            tag_name: tag,
            name: `Release ${tag}`,
            body: changelogContent || '',
            draft: false,
            prerelease: false,
            target_commitish: 'master' // Explicitly set release target to master branch
        };

        // Write release data to temp file
        const tempFile = path.join(__dirname, 'release-data.json');
        fs.writeFileSync(tempFile, JSON.stringify(releaseData));

        try {
            // Create GitHub release using curl
            console.log('Creating GitHub release...');
            const curlCommand = `curl -s -X POST -H "Authorization: token ${process.env.GITHUB_TOKEN}" -H "Content-Type: application/json" -d "@${tempFile.replace(/\\/g, '/')}" "https://api.github.com/repos/${repoFullName}/releases"`;
            
            const { stdout, stderr } = await execAsync(curlCommand);
            if (stderr) {
                console.error('Curl stderr:', stderr);
            }
            
            let release;
            try {
                release = JSON.parse(stdout);
                if (release.message) {
                    throw new Error(`GitHub API error: ${release.message}`);
                }
            } catch (e) {
                console.error('Failed to parse GitHub API response:', stdout);
                throw new Error('Invalid response from GitHub API');
            }
            
            if (!release || !release.upload_url) {
                console.error('Unexpected GitHub API response:', release);
                throw new Error('GitHub API response missing upload_url');
            }

            // Upload asset
            console.log('Uploading jar file...');
            const assetName = path.basename(jarPath);
            const uploadUrl = release.upload_url.replace('{?name,label}', `?name=${assetName}`);
            const uploadCommand = `curl -s -X POST -H "Authorization: token ${process.env.GITHUB_TOKEN}" -H "Content-Type: application/java-archive" --data-binary "@${jarPath.replace(/\\/g, '/')}" "${uploadUrl}"`;
            
            const { stdout: uploadStdout, stderr: uploadStderr } = await execAsync(uploadCommand);
            if (uploadStderr) {
                console.error('Upload stderr:', uploadStderr);
            }
            
            try {
                const uploadResponse = JSON.parse(uploadStdout);
                if (!uploadResponse.browser_download_url) {
                    throw new Error('Upload response missing download URL');
                }
                console.log(`Jar uploaded successfully: ${uploadResponse.browser_download_url}`);
            } catch (e) {
                console.error('Failed to parse upload response:', uploadStdout);
                throw new Error('Invalid response from GitHub upload API');
            }

            // Update master branch after successful release
            console.log('Updating master branch...');
            await execAsync('git checkout master');
            await execAsync('git merge develop');
            await execAsync('git push origin master');
            
            // Switch back to develop branch
            await execAsync('git checkout develop');
            
        } finally {
            // Cleanup temp file
            if (fs.existsSync(tempFile)) {
                fs.unlinkSync(tempFile);
            }
        }
        
        console.log(`GitHub release ${tag} created successfully with jar attachment!`);
        console.log('Master branch has been updated with the latest release.');
    } catch (error) {
        console.error('Failed to create GitHub release:', error);
        
        // Enhanced error handling for branch operations
        if (error.message.includes('git')) {
            console.error('Git operation failed. You may need to manually:');
            console.error('1. git checkout master');
            console.error('2. git merge develop');
            console.error('3. git push origin master');
            console.error('4. git checkout develop');
        }
        
        throw error;
    }
}

async function deploy() {
    const originalVersion = getCurrentVersion();
    let newVersion;
    let builtJarPath;
    
    try {
        // First verify GitHub configuration
        const repoFullName = await verifyGitHubConfig();
        
        console.log(`Current version: ${originalVersion}`);
        
        // Get release type from command line args
        const releaseType = process.argv[2] || 'patch';
        if (!['major', 'minor', 'patch'].includes(releaseType)) {
            throw new Error('Invalid release type. Use: major, minor, or patch');
        }
        
        // Increment version (without build number)
        newVersion = incrementVersion(originalVersion, releaseType);
        console.log(`New version will be: ${newVersion}`);
        
        // Get confirmation
        const readline = (await import('readline')).default;
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
        
        await new Promise((resolve) => {
            rl.question('Continue with release? (y/N) ', (answer) => {
                rl.close();
                if (answer.toLowerCase() !== 'y') {
                    console.log('Release cancelled');
                    process.exit(0);
                }
                resolve();
            });
        });

        // Update version in files
        updateVersion(newVersion);

        // Get changelog content
        const changelogContent = await getChangelogContent(newVersion);
        if (!changelogContent) {
            throw new Error('No changelog content found for this version');
        }
        
        // Build jar (without build number)
        builtJarPath = await buildJar(newVersion, false);
        console.log(`Built jar: ${builtJarPath}`);
        
        // Copy jar to project root
        const projectJarPath = manageProjectJars(builtJarPath);
        
        // Create GitHub release with jar
        await createGitHubRelease(newVersion, projectJarPath, changelogContent, repoFullName);
        
        console.log(' Release completed successfully!');
        console.log(`Version: ${newVersion}`);
        
    } catch (error) {
        console.error(' Release failed:', error.message);
        
        // Revert version if we had updated it
        if (newVersion && getCurrentVersion() === newVersion) {
            console.log(`\nReverting version back to ${originalVersion}...`);
            try {
                revertVersion(originalVersion);
                
                // Try to delete the tag if it was created
                try {
                    await execAsync(`git tag -d v${newVersion}`);
                    await execAsync(`git push origin :refs/tags/v${newVersion}`);
                    console.log(`Deleted tag v${newVersion}`);
                } catch (tagError) {
                    // Tag might not exist, ignore error
                }
                
                console.log('Version reverted successfully');
            } catch (revertError) {
                console.error('Failed to revert version:', revertError.message);
                console.error('Manual intervention may be required');
            }
        }
        
        process.exit(1);
    }
}

deploy();
