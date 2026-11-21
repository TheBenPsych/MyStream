// Rules for tools/audit/web_check.sh: only real mistakes fail, never style.
import globals from "globals";

const mistakes = {
  "no-undef": "error",
  "no-redeclare": ["error", { builtinGlobals: false }],
  "no-dupe-keys": "error",
  "no-dupe-args": "error",
  "no-dupe-else-if": "error",
  "no-const-assign": "error",
  "no-func-assign": "error",
  "no-import-assign": "error",
  "no-unreachable": "error",
  "no-unsafe-negation": "error",
  "use-isnan": "error",
  "valid-typeof": "error",
  "getter-return": "error",
};
const page = { ...globals.browser, Hls: "readonly", THREE: "readonly", EJS_player: "writable" };

export default [
  { files: ["main.js", "own/**/*.js"], languageOptions: { ecmaVersion: 2024, sourceType: "script", globals: page }, rules: mistakes },
  { files: ["desktop/**/*.js"], languageOptions: { ecmaVersion: 2024, sourceType: "commonjs", globals: { ...globals.node, ...globals.browser, Hls: "readonly" } }, rules: mistakes },
];
