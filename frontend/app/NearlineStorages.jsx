import React from 'react';
import axios from 'axios';
import {Bar} from 'react-chartjs-2';
import RefreshButton from "./common/RefreshButton.jsx";
import BytesFormatterImplementation from "./common/BytesFormatterImplementation.jsx";

class NearlineStorages extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            loading: false,
            lastError: null,
            storageData: [],
            countPoints: {},
            sizePoints: {}
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

    static colourValues = NearlineStorages.makeColourValues(5, 10);


    componentWillMount() {
        this.refresh();
    }

    processData(){
        let updatedCountPoints = {};
        let updatedSizePoints = {};

        this.state.storageData.map(storageEntry=>{
            storageEntry.states.map(storageState=>{
                if(updatedCountPoints.hasOwnProperty(storageState.state)) {
                    updatedCountPoints[storageState.state] = updatedCountPoints[storageState.state].concat(storageState.count);
                } else {
                    updatedCountPoints[storageState.state] = [storageState.count];
                }

                if(updatedSizePoints.hasOwnProperty(storageState.state)) {
                    updatedSizePoints[storageState.state] = storageState.totalSize < 0 ? updatedSizePoints[storageState.state].concat(0) : updatedSizePoints[storageState.state].concat(storageState.totalSize);
                } else {
                    updatedSizePoints[storageState.state] = storageState.totalSize < 0 ? [0] : [storageState.totalSize];
                }

            })
        });

        this.setState({countPoints: updatedCountPoints, sizePoints: updatedSizePoints});
    }

    refresh(){
        const fakeData = [{"storage":"VX-9","totalHits":373898,"totalSize":4.8691372696001E13,"states":[{"state":"CLOSED","count":373785,"totalSize":4.8624112814985E13},{"state":"UNKNOWN","count":62,"totalSize":5.7356821052E10},{"state":"LOST","count":44,"totalSize":-44.0},{"state":"BEING_READ","count":7,"totalSize":9.903060008E9}]},{"storage":"VX-4","totalHits":373741,"totalSize":6.226085412042E13,"states":[{"state":"CLOSED","count":373537,"totalSize":6.2097650958378E13},{"state":"UNKNOWN","count":108,"totalSize":1.46351189045E11},{"state":"LOST","count":89,"totalSize":1389345.0},{"state":"BEING_READ","count":7,"totalSize":1.6850583652E10}]}];

        this.setState({loading: true}, ()=>axios.get("/api/nearline/currentState").then(response=>{
            this.setState({loading: false, storageData: fakeData}, ()=>this.processData());
        }).catch(err=>{
            this.setState({loading: false, lastError: err})
        }))
    }

    render(){
        return <div className="container">
            <span className="controls-banner"><RefreshButton isRunning={this.state.loading} clickedCb={()=>this.refresh()}/></span>
            <Bar
                data={{
                    labels: this.state.storageData.map(entry=>entry.storage),
                    datasets: Object.keys(this.state.sizePoints).map((stateLabel,idx)=>{
                        return {
                            label: stateLabel,
                            backgroundColor: NearlineStorages.colourValues[idx],
                            data: this.state.sizePoints[stateLabel]
                        }
                    })
                }}
                options={{
                    title: {
                        display: true,
                        label: "Nearline storage sizes",
                        fontSize: 24
                    },
                    tooltips: {
                       callbacks: {
                           label: (tooltipItem,data)=>{
                               let xLabel, yLabel;

                                try {
                                    const result = BytesFormatterImplementation.getValueAndSuffix(tooltipItem.yLabel);
                                    yLabel = result[0] + result[1];
                                    xLabel = data.datasets[tooltipItem.datasetIndex].label;
                                    return xLabel + ": " + yLabel;
                                } catch(err){
                                    console.error(err);
                                    return tooltipItem.xLabel + ": " + tooltipItem.yLabel;
                                }
                           }
                       }
                    },
                    scales: {
                        yAxes: [{
                           scaleLabel: {
                               display: true,
                               labelString: "Size"
                           },
                            stacked: true,
                            ticks: {
                               callback: (value,index,series)=>{
                                   const result = BytesFormatterImplementation.getValueAndSuffix(value);
                                   return result[0] + result[1];
                               }
                            }
                        }],
                        xAxes: [{
                            stacked: true,
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

export default NearlineStorages;