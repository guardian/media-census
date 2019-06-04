import React from 'react';
import axios from 'axios';
import PropTypes from 'prop-types';
import {HorizontalBar} from "react-chartjs-2";
import RefreshButton from "./common/RefreshButton.jsx";

class CurrentStateStats extends React.Component {
    constructor(props){
        super(props);
        this.state = {
            loading: false,
            lastError: null,
            buckets: [],
            values: [],
            colourValues:[]
        }
    }

    static makeColourValues(count, offset){
        let values = [];
        for(let n=0;n<count;++n){
            let hue = (n/count)*360.0 + offset;
            values[n] = 'hsla(' + hue + ',75%,50%,0.6)'
        }
        return values;
    }


    componentWillMount() {
        this.refresh();
    }

    refresh(){
        this.setState({loading: true}, ()=>axios.get("/api/stats/replicas").then(result=>{
            this.setState({loading: false, lastError: null, buckets: result.data.buckets, values: result.data.values, colourValues: CurrentStateStats.makeColourValues(result.data.values.length,10)})
        }).catch(err=>{
            console.error(err);
            this.setState({loading: false, lastError: err});
        }))
    }

    render(){
        return <div className="current-stats-container">
            <RefreshButton isRunning={this.state.loading} clickedCb={()=>this.refresh()}/>
            <HorizontalBar data={{
                    datasets: this.state.buckets.map((bucketSize,idx)=>{return {
                        label: bucketSize + " copies",
                        data: [this.state.values[idx]],
                        backgroundColor: this.state.colourValues[idx]
                    }})
                }}
                           options={{
                               title: {
                                   display: true,
                                   text: "Current state",
                                   fontSize: 24
                               },
                               maintainAspectRatio: false,
                               scales: {
                                   yAxes: [{
                                       type: "category",
                                       gridLines: {
                                           display: true,
                                           color: "rgba(0,0,0,0.8)"
                                       },
                                       ticks: {
                                           autoSkip: false,
                                       },
                                       stacked: true
                                   }],
                                   xAxes: [{
                                       stacked: true
                                   }]
                               },
                               legend: {
                                   display: true,
                                   position: "bottom"
                               }
                           }}

                           height="200px"
            />
        </div>
    }
}

export default CurrentStateStats;