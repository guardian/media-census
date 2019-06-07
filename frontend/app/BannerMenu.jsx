import React from 'react';
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {Link} from 'react-router-dom';

class BannerMenu extends React.Component {
    render(){
        return <ul className="banner-menu">
            <li className="banner-menu-item"><Link to="/current"><FontAwesomeIcon icon="list-alt" className="banner-menu-icon"/>Current State</Link></li>
            <li className="banner-menu-item"><Link to="/history"><FontAwesomeIcon icon="history" className="banner-menu-icon"/>State History</Link></li>
            <li className="banner-menu-item"><Link to="/runs"><FontAwesomeIcon icon="ruler" className="banner-menu-icon"/>Runs admin</Link></li>
        </ul>
    }
}

export default BannerMenu;