// this file is a central collection of our dependencies

// this is a deno best practice https://deno.land/manual@v1.7.4/examples/manage_dependencies
// for  discussion of the performance implications (and why this doesn't matter much for this _app_) see https://github.com/denoland/deno/issues/6194

export * as path from "https://deno.land/std@0.170.0/path/mod.ts";
export * as yaml from "https://deno.land/std@0.170.0/encoding/yaml.ts";
export { v1 as uuid } from "https://deno.land/std@0.170.0/uuid/mod.ts";
export * as colors from "https://deno.land/std@0.170.0/fmt/colors.ts";

// note: it's a bit ugly that we have to foray into the private parts of the stdlib, but otherwise we can't configure
// the options we need
export { Type as YamlType } from "https://deno.land/std@0.170.0/encoding/_yaml/type.ts";
export { Schema as YamlSchema } from "https://deno.land/std@0.170.0/encoding/_yaml/schema.ts";

/**
 * 3rd party deps
 */
export {
  Command,
  CompletionsCommand,
  EnumType,
  Type,
} from "https://deno.land/x/cliffy@v0.25.7/command/mod.ts";
export {
  GithubProvider,
  UpgradeCommand,
} from "https://deno.land/x/cliffy@v0.25.7/command/upgrade/mod.ts";
export type { GithubProviderOptions } from "https://deno.land/x/cliffy@v0.25.7/command/upgrade/mod.ts";
export { Table } from "https://deno.land/x/cliffy@v0.25.7/table/mod.ts";
export {
  Input,
  prompt as prompt,
  Select,
} from "https://deno.land/x/cliffy@v0.25.7/prompt/mod.ts";
export type { SelectValueOptions } from "https://deno.land/x/cliffy@v0.25.7/prompt/mod.ts";
export { List } from "https://deno.land/x/cliffy@v0.25.7/prompt/list.ts";
