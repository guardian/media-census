import React from 'react';
import {shallow, render} from 'enzyme';
import NearlineStorages from "../app/NearlineStorages.jsx";
import {numberWithCommas} from "../app/NearlineStorages.jsx";

describe("NearlineStorages.processData", ()=>{
    const fakeData = [{"storage":"VX-9","totalHits":373898,"totalSize":4.8691372696001E13,"states":[{"state":"CLOSED","count":373785,"totalSize":4.8624112814985E13},{"state":"UNKNOWN","count":62,"totalSize":5.7356821052E10},{"state":"LOST","count":44,"totalSize":-44.0},{"state":"BEING_READ","count":7,"totalSize":9.903060008E9}]},{"storage":"VX-4","totalHits":373741,"totalSize":6.226085412042E13,"states":[{"state":"CLOSED","count":373537,"totalSize":6.2097650958378E13},{"state":"UNKNOWN","count":108,"totalSize":1.46351189045E11},{"state":"LOST","count":89,"totalSize":1389345.0},{"state":"BEING_READ","count":7,"totalSize":1.6850583652E10}]}];

    it("should break down incoming data from the state", (done)=>{
        const rendered = shallow(<NearlineStorages/>);

        rendered.instance().setState({loading: false, storageData: fakeData}, ()=>{
            rendered.instance().processData();

            try {
                expect(rendered.instance().state.countPoints.CLOSED.length).toEqual(2);
                expect(rendered.instance().state.countPoints.BEING_READ.length).toEqual(2);
                expect(rendered.instance().state.sizePoints.CLOSED.length).toEqual(2);

                expect(rendered.instance().state.countPoints.UNKNOWN).toEqual([ 62, 108 ]);
                expect(rendered.instance().state.sizePoints.UNKNOWN).toEqual([ 57356821052, 146351189045 ]);
                done();
            } catch(err){
                console.log(rendered.instance().state);
                done.fail(err);
            }
        });

    })
});

describe("numberWithCommas", ()=>{
    it("should convert a long number with thousands separators", ()=>{
        expect(numberWithCommas(883496)).toEqual("883,496");
    })
});