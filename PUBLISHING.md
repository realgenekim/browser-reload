# Publishing browser-reload

Instructions for publishing new versions of the browser-reload library to GitHub.

## Prerequisites

- GitHub CLI (`gh`) installed and authenticated
- Git repository with commits ready to publish

## Publishing a New Release

### 1. Commit Your Changes

```bash
git add .
git commit -m "Your commit message"
```

### 2. Get the Current Commit SHA

```bash
git rev-parse HEAD
```

This outputs the full SHA (e.g., `a794c2dd1dc505d2a400419b937d8df04c93d494`) that users will reference in their `deps.edn`.

### 3. Tag the Release

Create an annotated tag with version and release notes:

```bash
git tag -a v0.2.0 -m "Release v0.2.0

New features:
- Feature 1
- Feature 2

Bug fixes:
- Fix 1
- Fix 2
"
```

**Version numbering** (Semantic Versioning):
- **v0.x.0** - Breaking changes (API changes, removed features)
- **v0.0.x** - New features, enhancements (backwards compatible)
- **v0.0.x** - Bug fixes, documentation (no new features)

### 4. Push Commits and Tags

```bash
# Push commits
git push origin main

# Push tags
git push origin v0.2.0

# Or push both at once
git push origin main --tags
```

### 5. Update Documentation

Update the README.md with the new SHA if installation instructions reference a specific commit:

```clojure
;; In README.md installation example:
browser-reload/browser-reload {:git/url "https://github.com/realgenekim/browser-reload"
                               :git/sha "NEW-SHA-HERE"}
```

Commit and push the documentation update:

```bash
git add README.md
git commit -m "Update README with latest SHA"
git push origin main
```

## First-Time Publishing

If you haven't published the repository yet:

### 1. Create GitHub Repository

```bash
gh repo create realgenekim/browser-reload \
  --public \
  --source=. \
  --description="Browser auto-reload for Clojure web development. Edit → Save → Browser refreshes automatically." \
  --push
```

This creates the repository, sets up the remote, and pushes all commits.

### 2. Tag Initial Release

```bash
git tag -a v0.1.0 -m "Initial release: browser-reload v0.1.0

Features:
- Browser auto-reload via file watching + HTTP polling
- Ring middleware (wrap-reload-script)
- File watcher integration (start-file-watcher!)
- Manual REPL trigger (trigger-reload!)
- Zero-config setup for Ring/Reitit apps
"

git push origin v0.1.0
```

### 3. Get SHA for Users

```bash
git rev-parse HEAD
```

Users add this to their `deps.edn`:

```clojure
{:deps {browser-reload/browser-reload
         {:git/url "https://github.com/realgenekim/browser-reload"
          :git/sha "a794c2dd1dc505d2a400419b937d8df04c93d494"}}}
```

## Updating Consumer Projects

After publishing a new version, consumers can update to the new SHA:

### 1. Find the Latest SHA

On GitHub:
- Go to: https://github.com/realgenekim/browser-reload
- Click "Commits" or "Tags"
- Copy the commit SHA or tag SHA

Or locally:
```bash
git log --oneline | head -1
git rev-parse v0.2.0  # Get SHA for specific tag
```

### 2. Update deps.edn in Consumer Project

```clojure
;; Change the :git/sha to the new commit SHA
browser-reload/browser-reload {:git/url "https://github.com/realgenekim/browser-reload"
                               :git/sha "NEW-SHA-HERE"}
```

### 3. Clear Classpath Cache

```bash
rm -rf .cpcache
```

### 4. Test

```bash
clj -M:test  # Or whatever your test command is
```

## Release Checklist

Before publishing:

- [ ] All tests pass locally
- [ ] Code is formatted (`make format` if applicable)
- [ ] README is up to date
- [ ] CHANGELOG is updated (if you have one)
- [ ] Version number follows semantic versioning
- [ ] Release notes describe changes clearly

After publishing:

- [ ] Verify tag appears on GitHub
- [ ] Test installation in a consumer project
- [ ] Update consumer projects to new version
- [ ] Announce release (if applicable)

## Troubleshooting

### Tag already exists

```bash
# Delete local tag
git tag -d v0.2.0

# Delete remote tag
git push origin :refs/tags/v0.2.0

# Create new tag
git tag -a v0.2.0 -m "..."
git push origin v0.2.0
```

### Wrong SHA in deps.edn

Users might see:
```
Error building classpath. Could not find artifact...
```

Solution: Ensure the SHA exists on GitHub:
```bash
git log --oneline | grep <short-sha>
```

If not, push the commit:
```bash
git push origin main
```

## Quick Reference

```bash
# Get current SHA
git rev-parse HEAD

# Tag and push
git tag -a v0.2.0 -m "Release notes"
git push origin v0.2.0

# List all tags
git tag -l

# Show tag details
git show v0.2.0
```

## See Also

- [deps.edn Git Dependencies](https://clojure.org/guides/deps_and_cli#_using_git_libraries)
- [Semantic Versioning](https://semver.org/)
- [GitHub CLI Documentation](https://cli.github.com/manual/)
