import React from 'react';
import {shallow, mount} from 'enzyme';
import VSFileSearchView from "../app/VSFileSearchView.jsx";
import moxios from "moxios";

describe("VSFileSearchView.splitFilePath", ()=>{
    it("should sepaate a path into filename and basepath components", ()=>{
        const result = VSFileSearchView.splitFilePath("/path/to/something.mp4");
        expect(result).toEqual(["/path/to","something.mp4"])
    });

    it("should return filename only if there is no path", ()=>{
        const result = VSFileSearchView.splitFilePath("something.mp4");
        expect(result).toEqual(["","something.mp4"])
    });

    it("should not rely on a leading /", ()=>{
        const result = VSFileSearchView.splitFilePath("path/to/something.mp4");
        expect(result).toEqual(["path/to","something.mp4"])
    });
});