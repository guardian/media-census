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
            colourValues:[],
            unattachedCount: 0,
            unimportedCount: 0
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

    /**
     * By definition, the "zero" replicas bucket also counts unimported and unattached items. So, we subtract them out here
     * and put them into seperate buckets.
     **/
    postProcessData(responseData){
        const zeroBucketIndex = responseData.buckets.indexOf(0);
        const zeroCount = responseData.values[zeroBucketIndex];

        let updatedValues = responseData.values.slice(0);   //clone out values
        updatedValues[zeroBucketIndex] = zeroCount - responseData.extraData.unimported - responseData.extraData.unattached;


        return {
            buckets: ["Unimported", "Unattached"].concat(responseData.buckets.map(v=>v.toString())),
            values: [
                responseData.extraData.unimported,
                responseData.extraData.unattached,
            ].concat(updatedValues)
        }
    }

    refresh(){
        this.setState({loading: true}, ()=>axios.get("/api/stats/unattached").then(result=>{
            const postProcessed = this.postProcessData(result.data);

            this.setState({loading: false,
                lastError: null,
                buckets: postProcessed.buckets,
                values: postProcessed.values,
                colourValues: CurrentStateStats.makeColourValues(result.data.values.length+2,10)
            })
        }).catch(err=>{
            console.error(err);
            this.setState({loading: false, lastError: err});
        }))
    }

    render(){
        return <div className="current-stats-container">
            {/*<RefreshButton isRunning={this.state.loading} clickedCb={()=>this.refresh()} style={{display: "inline"}}/>*/}
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

            />
        </div>
    }
}

export default CurrentStateStats;