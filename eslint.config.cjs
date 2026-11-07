module.exports = [
  {
    ignores: [
      "**/node_modules/**",
      "**/build/**",
      "**/.*/**",
      "google-cloud-sdk/**",
      "cpanel-package/**",
      "jananisync-cpanel/**",
      "jananisync-v1/**",
      "public/**",
    ],
  },
  {
    files: ["**/*.js", "**/*.cjs", "**/*.mjs"],
    languageOptions: {ecmaVersion: "latest"},
    rules: {
      "constructor-super": "error",
      "for-direction": "error",
      "no-async-promise-executor": "error",
      "no-constant-binary-expression": "error",
      "no-dupe-args": "error",
      "no-dupe-keys": "error",
      "no-duplicate-case": "error",
      "no-invalid-regexp": "error",
      "no-unreachable": "error",
      "valid-typeof": "error",
    },
  },
  {
    files: ["webApp/webpack.config.d/*.js"],
    languageOptions: {sourceType: "commonjs"},
  },
];
