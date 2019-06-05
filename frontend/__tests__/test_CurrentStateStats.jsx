import React from 'react';
import moxios from 'moxios';
import {shallow, mount} from 'enzyme';
import CurrentStateStats from "../app/CurrentStateStats.jsx";

describe("CurrentStateStats", ()=>{
    beforeEach(()=>moxios.install());
    afterEach(()=>moxios.uninstall());

    it("should correctly load and post-process data from the server", done=>{
        const rendered = shallow(<CurrentStateStats/>);

        return moxios.wait(()=>{
            const request = moxios.requests.mostRecent();

            try{
                expect(request.url).toEqual("/api/stats/unattached");
            } catch (err){
                done.fail(err);
            }

            request.respondWith({
                status: 200,
                response: {"status": "ok", "buckets":[1.0],"values":[3573],"extraData":{"unimported":10,"unattached":20}}
            }).then(()=> {
                expect(rendered.instance().state.buckets).toEqual(["Unimported", "Unattached", 1.0]);
                expect(rendered.instance().state.values).toEqual([10, 20, 3573]);
                done();
            }).catch(err=>{
                console.error(err);
                done.fail(err);
            });
        })
    });

    it("should correctly load and post-process data from the server with a zero-count", done=>{
        const rendered = shallow(<CurrentStateStats/>);

        return moxios.wait(()=>{
            const request = moxios.requests.mostRecent();

            try{
                expect(request.url).toEqual("/api/stats/unattached");
            } catch (err){
                done.fail(err);
            }

            request.respondWith({
                status: 200,
                response: {"status": "ok", "buckets":[0.0, 1.0],"values":[360, 3573],"extraData":{"unimported":10,"unattached":20}}
            }).then(()=> {
                expect(rendered.instance().state.buckets).toEqual(["Unimported", "Unattached", 0, 1.0]);
                expect(rendered.instance().state.values).toEqual([10, 20, 330, 3573]);
                done();
            }).catch(err=>{
                console.error(err);
                done.fail(err);
            });
        })
    });
});