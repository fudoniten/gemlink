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
          gemlink = helpers.packages."${system}".mkClojureLib {
            name = "org.fudo/gemlink";
            src = ./.;
          };
        };

        devShells = rec {
          default = updateDeps;
          updateDeps = pkgs.mkShell {
            buildInputs = with helpers.packages."${system}";
              [ updateClojureDeps ];
          };
        };

        checks = {
          clojureTests = pkgs.runCommand "clojure-tests" { } ''
            mkdir -p $TMPDIR
            HOME=$TMPDIR
            ${pkgs.clojure}/bin/clojure -X:test
            touch $out
          '';
        };
      });
}
