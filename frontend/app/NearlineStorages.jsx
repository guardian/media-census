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
                    updatedSizePoints[storageState.state] = updatedSizePoints[storageState.state].concat(storageState.totalSize);
                } else {
                    updatedSizePoints[storageState.state] = [storageState.totalSize];
                }

            })
        });

        this.setState({countPoints: updatedCountPoints, sizePoints: updatedSizePoints});
    }

    refresh(){
        this.setState({loading: true}, ()=>axios.get("/api/nearline/currentState").then(response=>{
            this.setState({loading: false, storageData: response.data}, ()=>this.processData());
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
                            ticks: {
                               callback: (value,index,series)=>{
                                   const result = BytesFormatterImplementation.getValueAndSuffix(value);
                                   return result[0] + result[1];
                               }
                            }
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