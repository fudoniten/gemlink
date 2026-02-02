{
  description = "GemLink: a simple Gemini server in Clojure.";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-25.11";
    utils.url = "github:numtide/flake-utils";
    helpers = {
      url = "github:fudoniten/fudo-nix-helpers";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { nixpkgs, utils, helpers, ... }:
    utils.lib.eachDefaultSystem (system:
      let pkgs = nixpkgs.legacyPackages."${system}";
      in {
        packages = rec {
          default = gemlink;
          gemlink = helpers.legacyPackages."${system}".mkClojureLib {
            name = "org.fudo/gemlink";
            src = ./.;
          };
        };

        devShells = rec {
          default = updateDeps;
          updateDeps = pkgs.mkShell {
            buildInputs = with helpers.legacyPackages."${system}";
              [ (updateClojureDeps { }) ];
          };
        };

        # Tests are best run locally or in CI where network access is available:
        #   clojure -M:test
        # 
        # Running tests in nix flake check with the current clj-nix setup is complex
        # because the Clojure CLI tries to resolve dependencies at runtime even with
        # a lockfile. This would require deeper integration with clj-nix's internals.
        checks = {};

      });
}
