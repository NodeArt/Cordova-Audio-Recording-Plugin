/**
 * Created by Ivan on 26.12.2016.
 */
//#!/usr/bin/env node
"use strict";

module.exports = function(ctx) {
    const fs      = ctx.requireCordovaModule('fs'),
          path    = ctx.requireCordovaModule('path'),
          {spawn} = ctx.requireCordovaModule('child_process');

    function readFile(path) {
        try {
            return fs.readFileSync(path).toString();
        } catch(err) {
            return err;
        }
    }

    /**
     * This function changes the settings of c++ compiler to those appropriate for FMOD launch
     * @param file
     * @param pathToXCodeProj
     */
    function setCppCompiler(file, pathToXCodeProj) {
        let paramsToBeChanged = {
            CLANG_CXX_LANGUAGE_STANDARD : "\"c++14\"",
            CLANG_CXX_LIBRARY : "\"libc++\""
        };

        if (file.match(new RegExp("CLANG_CXX_LANGUAGE_STANDARD", "g"))) {
            Object.keys(paramsToBeChanged).forEach(key => {
                let defaultRegExp = new RegExp(`${key} = "compiler-default"`, 'g'),
                    newStr = `${key} = ${paramsToBeChanged[key]}`;
                file = file.replace(defaultRegExp, newStr);
            });
        } else {
            let start = 'LaunchImage;',
                res = Object.keys(paramsToBeChanged)
                            .reduce((acum, key) => acum += `\n\t\t\t\t${key} = ${paramsToBeChanged[key]};`, start);
            console.log(start, res);
            file = file.replace(new RegExp(start, 'gi'), res);
        }
        fs.writeFileSync(pathToXCodeProj, file);
    }

    if (ctx.opts.platforms.indexOf('ios') < 0) {
        const iosRoot = path.join(ctx.opts.projectRoot, 'platforms/ios'),
            //todo: set path to xcodeproj
              pathToXCodeProj = `${iosRoot}/`;
        try {
            let xCodeProj = readFile(pathToXCodeProj);
            setCppCompiler(xCodeProj, pathToXCodeProj);
        } catch(err) {
            console.error(err);
        }
    }

    const command = spawn(path.join(ctx.plugin.dir, 'download_repidjson.sh'));
    command.on('close', function () {
        console.log('submodule installed');
    })
};