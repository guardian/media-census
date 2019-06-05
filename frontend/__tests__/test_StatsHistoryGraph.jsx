import React from 'react';
import {shallow, mount} from 'enzyme';
import StatsHistoryGraph from "../app/StatsHistoryGraph.jsx";

describe("StatsHistoryGraph.objectToQueryString", ()=>{
   it("should take a set of mappings and make a query string", ()=>{
       const rendered = shallow(<StatsHistoryGraph/>);
       const result = rendered.instance().objectToQueryString({"key1": "value1", key2: "value2"});

       expect(result).toEqual("?key1=value1&key2=value2");
   });

   it("should return an empty string if there are no keys", ()=>{
       const rendered = shallow(<StatsHistoryGraph/>);
       const result = rendered.instance().objectToQueryString({});
       expect(result).toEqual("");
   });

   it("should return an empty string if passed NULL", ()=>{
       const rendered = shallow(<StatsHistoryGraph/>);
       const result = rendered.instance().objectToQueryString(null);
       expect(result).toEqual("");
   })
});