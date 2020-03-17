import React from 'react';
import axios from 'axios';
import {Pie,Bar,HorizontalBar} from "react-chartjs-2";
import NearlineControlsBanner from "./common/NearlineControlsBanner.jsx";
import ProjectSearchView from "./ProjectSearchView.jsx";
import BytesFormatterImplementation from "./common/BytesFormatterImplementation.jsx";

class NearlineUnclog extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            loading: false,
            lastError:null,
            chartMode: NearlineControlsBanner.CHART_MODE_COUNT,
            allData: null,
            showProjectsList: false,
            selectedProjectStatus: null
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

    static colourValues = NearlineUnclog.makeColourValues(19, 10);

    static makeHoverColourValues(count, offset){
        let values = [];
        for(let n=0;n<count;++n){
            let hue = (n/count)*360.0 + offset;
            values[n] = 'hsla(' + hue + ',75%,50%,0.8)'
        }
        return values;
    }

    static hoverColourValues = NearlineUnclog.makeHoverColourValues(19, 10);

    refresh(){
        this.setState({loading: true}, ()=>axios.get("/api/unclog/mediaStatus").then(response=>{
            this.setState({loading: false, allData: response.data});
        }).catch(err=>{
            this.setState({loading: false, lastError: err})
        }));
    }

    componentWillMount() {
        this.refresh();
    }

    refreshChartData(){
        this.refresh();
    }

    render(){
        return <div>
            <NearlineControlsBanner dataMode={this.state.chartMode}
                                    dataModeChanged={evt=>this.setState({chartMode: parseInt(evt.target.value)},()=>this.refreshChartData())}
                                    isRunning={this.state.loading}
                                    refreshClicked={this.refresh}
                                    />
            <HorizontalBar data={{

                datasets: this.state.allData ? this.state.allData.map((datapoint,idx)=>{
                    let data;
                    if(this.state.chartMode===NearlineControlsBanner.CHART_MODE_COUNT){
                        data = [datapoint.count];
                    } else if(this.state.chartMode===NearlineControlsBanner.CHART_MODE_SIZE){
                        data = [datapoint.size];
                    } else {
                        throw "Invalid mode for component, should be count or size."
                    }
                    return {data: data,label:datapoint.label, backgroundColor: NearlineUnclog.colourValues[idx*2], hoverBackgroundColor: NearlineUnclog.hoverColourValues[idx*2] }
                }) : [],
                labels: ["Files"]
            }}
           height={50}
           options={{scales: {
                   yAxes: [{stacked: true}],
                   xAxes: [{
                       stacked: true,
                       labelString: this.state.chartMode===NearlineControlsBanner.CHART_MODE_SIZE ? "Total size" : "File count",
                       ticks: {
                           callback: (value,index,series)=>
                               this.state.chartMode===NearlineControlsBanner.CHART_MODE_SIZE ? Math.floor(value/1099511627776) + "Tib" : value
                       }
                   }]

               },
               tooltips: {
                   callbacks: {
                       label: (tooltipItem,data)=>{
                           let xLabel=data.datasets[tooltipItem.datasetIndex].label;
                           let yLabel=data.datasets[tooltipItem.datasetIndex].data;

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
           getElementAtEvent = {elems=>{
               const bar = elems[0];
               console.log("You clicked on bar number ", bar._datasetIndex, bar._model.datasetLabel);
               this.setState({selectedProjectStatus: bar._model.datasetLabel, showProjectsList: true});
           }}

            />
            <div>
                <br />
            <ProjectSearchView projectStatus={this.state.selectedProjectStatus} visible={this.state.showProjectsList}/>
        </div>
        </div>

    }
}

export default NearlineUnclog;