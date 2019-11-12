import React from 'react';
import axios from 'axios';
import {Pie,Bar,HorizontalBar} from "react-chartjs-2";
import BytesFormatterImplementation from "./common/BytesFormatterImplementation.jsx";
import moment from 'moment';
import NearlineControlsBanner from "./common/NearlineControlsBanner.jsx";
import VSFileSearchView from "./VSFileSearchView.jsx";

class NearlineStorageMembership extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            loading: false,
            lastError:null,
            totalCount:0,
            noMembership:0,
            totalSize:0,
            states:[],
            noMembershipTimeBreakdown: [],
            pieData: null,
            barData: null,
            summaryData: null,
            chartMode: NearlineControlsBanner.CHART_MODE_COUNT,
            showFilesList: false,
            selectedDateSection: null
        };

        this.refresh = this.refresh.bind(this);
    }

    static makeColourValues(count, offset){
        let values = [];
        for(let n=0;n<count;++n){
            let hue = (n/count)*360.0 + offset;
            values[n] = 'hsla(' + hue + ',75%,50%,0.6)'
        }
        return values;
    }

    static colourValues = NearlineStorageMembership.makeColourValues(10, 10);

    /**
     * updates pieData and barData based on the current chart mode and the data downloaded from the server
     */
    refreshChartData(){
        const fileStateDatapoints = this.state.states.map(entry=>this.state.chartMode===NearlineControlsBanner.CHART_MODE_COUNT ? entry.count : entry.totalSize);
        const fileStateLabels = this.state.states.map(entry=>entry.state);

        const timeBreakdownDatapoints = this.state.noMembershipTimeBreakdown.map(entry=>entry.doc_count);
        const timeBreakdownLabels = this.state.noMembershipTimeBreakdown.map(entry=>entry.date);

        const allStateSizes = this.state.states.reduce((acc, entry)=>acc+entry.totalSize, 0);

        const summaryDataDatapoints = [
            this.state.chartMode===NearlineControlsBanner.CHART_MODE_COUNT ? this.state.totalCount-this.state.noMembership : this.state.totalSize - allStateSizes,
            this.state.chartMode===NearlineControlsBanner.CHART_MODE_COUNT ? this.state.noMembership : allStateSizes
        ];

        this.setState({
            pieData: {
                datapoints: fileStateDatapoints,
                labels: fileStateLabels
            },
            barData: {
                datapoints: timeBreakdownDatapoints,
                labels: timeBreakdownLabels
            },
            summaryData: {
                datapoints: summaryDataDatapoints,
                labels: ["Attached to items","Not attached to items"]
            }
        })
    }

    refresh() {
        this.setState({loading: true},()=>axios.get("/api/nearline/membership").then(response=>{
            this.setState({
                loading: false,
                lastError: null,
                totalCount: response.data.totalCount,
                noMembership: response.data.noMembership,
                totalSize: response.data.totalSize,
                states: response.data.states,
                noMembershipTimeBreakdown: response.data.noMembershipTimeBreakdown
            }, ()=>this.refreshChartData())
        }).catch(err=>{
            console.error(err);
            this.setState({loading: false, lastError: err})
        }));
    }

    componentWillMount() {
        this.refresh();
    }

    render(){
        return <div>
            <NearlineControlsBanner dataMode={this.state.chartMode}
                                    dataModeChanged={evt=>this.setState({chartMode: parseInt(evt.target.value)},()=>this.refreshChartData())}
                                    isRunning={this.state.loading}
                                    refreshClicked={this.refresh}
                                    />

            <div style={{width: "100vw",  overflow:"hidden"}}>
                <HorizontalBar data={{

                                    datasets: this.state.summaryData ? this.state.summaryData.datapoints.map((datapoint,idx)=>{
                                        return {data: [datapoint],label:this.state.summaryData.labels[idx], backgroundColor: NearlineStorageMembership.colourValues[idx*2]}
                                    }) : [],
                                    labels: ["Summary"]
                                }}
                               height={50}
                                options={{
                                    scales: {
                                        yAxes: [{
                                            labelString: "",
                                            scaleLabel: {
                                                display: false,
                                            },
                                            stacked: true
                                        }],
                                        xAxes: [{
                                            labelString: "",
                                            scaleLabel: {
                                                display: false,
                                                labelString: "File count",
                                            },
                                            ticks: {
                                                callback: (value,index,series)=>{
                                                    if(this.state.chartMode===NearlineControlsBanner.CHART_MODE_SIZE) {
                                                        const result = BytesFormatterImplementation.getValueAndSuffix(value);
                                                        return result[0] + result[1];
                                                    } else {
                                                        return value;
                                                    }
                                                }
                                            },
                                            stacked: true
                                        }]
                                    },
                                    tooltips: {
                                        callbacks: {
                                            label: (tooltipItem,data)=>{
                                                let xLabel=data.labels[tooltipItem.index];
                                                let yLabel=data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index];

                                                console.log(tooltipItem);
                                                console.log(data);
                                                console.log(xLabel, yLabel);

                                                if(this.state.chartMode===NearlineControlsBanner.CHART_MODE_SIZE) {
                                                    try {
                                                        const result = BytesFormatterImplementation.getValueAndSuffix(yLabel);
                                                        yLabel = result[0] + result[1];
                                                        return xLabel + ": " + yLabel;
                                                    } catch (err) {
                                                        console.error(err);
                                                        return xLabel + ": " + yLabel;
                                                    }
                                                } else {
                                                    return xLabel + ": " + yLabel;
                                                }
                                            }
                                        }
                                    }
                                }}
                />
            </div>

            <div style={{width: "64vw", overflow:"hidden", display:"inline-block"}}>
                <Bar data={{
                    datasets: [{data:this.state.barData ? this.state.barData.datapoints : [], label: "Files with no membership"}],
                    labels: this.state.barData ? this.state.barData.labels : []
                }}
                     options={{
                         scales: {
                             yAxes: [{
                                 scaleLabel: {
                                     display: true,
                                     labelString: "File count",
                                 }
                             }],
                             xAxes: [{
                                 labelString: "Date",
                                 ticks: {
                                     callback: (value,index,series)=>{
                                         return moment(value).format("Do MMM YYYY")
                                     }
                                 },
                                 stacked: false
                             }]
                         },
                         legend: {
                             display: true,
                             position: "bottom"
                         }
                     }}

                     onElementsClick={elems=>{
                         const bar = elems[0];
                         console.log("You clicked on bar number ", bar._index, this.state.barData.labels[bar._index]);
                         this.setState({selectedDateSection: this.state.barData.labels[bar._index], showFilesList: true});
                     }}
                     />
            </div>
            <div style={{width: "33vw",overflow:"hidden", float:"right"}}>
                <Pie data={{
                    datasets: [{data:this.state.pieData ? this.state.pieData.datapoints : []}],
                    labels: this.state.pieData ? this.state.pieData.labels : []
                }}
                     options={{
                         title: {
                             display: true,
                             label: "Files without item membership by file state",
                             fontSize: 24,
                             color: "#000000"
                         },
                         tooltips: {
                             callbacks: {
                                 label: (tooltipItem,data)=>{
                                     let xLabel=data.labels[tooltipItem.index];
                                     let yLabel=data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index];

                                     console.log(tooltipItem);
                                     console.log(data);
                                     console.log(xLabel, yLabel);

                                     if(this.state.chartMode===NearlineControlsBanner.CHART_MODE_SIZE) {
                                         try {
                                             const result = BytesFormatterImplementation.getValueAndSuffix(yLabel);
                                             yLabel = result[0] + result[1];
                                             return xLabel + ": " + yLabel;
                                         } catch (err) {
                                             console.error(err);
                                             return xLabel + ": " + yLabel;
                                         }
                                     } else {
                                         return xLabel + ": " + yLabel;
                                     }
                                 }
                             }
                         },
                     }}

                />
            </div>

            <div>
                <VSFileSearchView startingTime={this.state.selectedDateSection} durationTime={30*3600*24} visible={this.state.showFilesList}/>
            </div>
        </div>
    }
}

export default NearlineStorageMembership;