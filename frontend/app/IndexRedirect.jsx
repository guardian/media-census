import React from 'react';
import {Redirect} from 'react-router-dom';

class IndexRedirect extends React.Component {
    render(){
        return <Redirect to="/current"/>
    }
}

export default IndexRedirect;