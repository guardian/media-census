import React from 'react';
import axios from 'axios';
import PropTypes from 'prop-types';
import {HorizontalBar} from "react-chartjs-2";
import RefreshButton from "./common/RefreshButton.jsx";
import RunsInProgress from "./RunsInProgress.jsx";
import TimestampDiffComponent from "./common/TimestampDiffComponent.jsx";

class CurrentStateStats extends React.Component {
    static COUNT_MODE=1;
    static SIZE_MODE=2;

    constructor(props){
        super(props);
        this.state = {
            loading: false,
            lastError: null,
            buckets: [],
            values: [],
            totalSizes: [],
            colourValues:[],
            unattachedCount: 0,
            unimportedCount: 0,
            lastRunSuccess: null,
            lastDeleteRunSuccess: null,
            mode: CurrentStateStats.SIZE_MODE
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

        //FIXME: backend is not yet reporting size data for unimported/unattached
        let updatedSizes = responseData.totalSize;

        return {
            buckets: ["Unimported", "Unattached"].concat(responseData.buckets.map(v=>v.toString())),
            values: [
                responseData.extraData.unimported,
                responseData.extraData.unattached,
            ].concat(updatedValues),
            totalSizes: [
                0,
                0
            ].concat(updatedSizes)
        }
    }

    refresh(){
        const loadingFuture = Promise.all([
            axios.get("/api/stats/unattached"),
            axios.get("/api/jobs/CensusScan/lastSuccess"),
            axios.get("/api/jobs/DeletedScan/lastSuccess")
        ]);

        this.setState({loading: true}, ()=>loadingFuture.then(result=>{
            const unattachedResult = result[0];
            const completedJobsResult = result[1];
            const deleteJobsResult = result[2];

            const postProcessed = this.postProcessData(unattachedResult.data);

            this.setState({loading: false,
                lastError: null,
                buckets: postProcessed.buckets,
                values: postProcessed.values,
                totalSizes: postProcessed.totalSizes,
                colourValues: CurrentStateStats.makeColourValues(unattachedResult.data.values.length+2,10),
                lastRunSuccess: completedJobsResult.data.entry,
                lastDeleteRunSuccess: deleteJobsResult.data.entry
            })
        }).catch(err=>{
            console.error(err);
            this.setState({loading: false, lastError: err});
        }))
    }

    render(){
        return <div>
            <RunsInProgress/>
            {/*<RefreshButton isRunning={this.state.loading} clickedCb={()=>this.refresh()} style={{display: "inline"}}/>*/}
            <div  className="current-stats-container">
            <HorizontalBar data={{
                    datasets: this.state.buckets.map((bucketSize,idx)=>{
                        let data;
                        if(this.state.mode===CurrentStateStats.COUNT_MODE){
                            data = this.state.values[idx];
                        } else if(this.state.mode===CurrentStateStats.SIZE_MODE){
                            data = this.state.totalSizes[idx];
                        } else {
                            throw "Invalid mode for component, should be count or size."
                        }
                        return {
                            label: bucketSize + " copies",
                            data: [data],
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
                               tooltips: {
                                   callbacks: {
                                       label: (tooltipItem, data)=>{
                                           console.log(tooltipItem);
                                           console.log(data);
                                           let xLabel,yLabel;

                                           if(tooltipItem.xLabel && this.state.mode===CurrentStateStats.SIZE_MODE){
                                               xLabel = Math.floor(tooltipItem.xLabel/1073741824) + "Gib";
                                               yLabel = data.datasets[tooltipItem.datasetIndex].label;
                                           } else {
                                               xLabel=tooltipItem.xLabel;
                                               yLabel = data.datasets[tooltipItem.datasetIndex].label;
                                           }
                                           return yLabel + ": " + xLabel;
                                       }
                                   }
                               },
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
                                       stacked: true,
                                       labelString: this.state.mode===CurrentStateStats.SIZE_MODE ? "Total size" : "File count",
                                       ticks: {
                                           callback: (value,index,series)=>
                                               this.state.mode===CurrentStateStats.SIZE_MODE ? Math.floor(value/1073741824) + "Gib" : value
                                       }
                                   }]
                               },
                               legend: {
                                   display: true,
                                   position: "bottom"
                               }
                           }}
                           redraw={true}

            />
            </div>
            <div>
                <select value={this.state.mode} onChange={evt=>{
                    console.log("New value", parseInt(evt.target.value));
                    this.setState({mode: parseInt(evt.target.value)})
                }}>
                    <option value={CurrentStateStats.SIZE_MODE}>View total data size</option>
                    <option value={CurrentStateStats.COUNT_MODE}>View file count</option>
                </select>
            </div>
            <ul className="current-stats-bulletpoints">
                {this.state.lastRunSuccess ?
                    <li>Last successful census scan completed <TimestampDiffComponent
                        endTime={this.state.lastRunSuccess.scanFinish}/> and
                        found {this.state.lastRunSuccess.itemsCounted} items.</li> :
                    <li>There have not been any successful census scan runs yet!</li>
                }
                {this.state.lastDeleteRunSuccess ?
                    <li>Last successful deletion scan completed <TimestampDiffComponent
                        endTime={this.state.lastDeleteRunSuccess.scanFinish}/>.</li> :
                    <li>There have not been any successful deletion scan runs yet!</li>
                }
            </ul>
        </div>
    }
}

export default CurrentStateStats;